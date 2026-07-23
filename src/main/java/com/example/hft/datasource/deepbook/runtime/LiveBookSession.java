package com.example.hft.datasource.deepbook.runtime;

import com.example.hft.datasource.DataSourceModuleVersion;
import com.example.hft.datasource.book.BookQuality;
import com.example.hft.datasource.deepbook.DeepBookSourceDefinition;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicLong;


public final class LiveBookSession implements AutoCloseable {
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration DISPATCH_DRAIN_TIMEOUT = Duration.ofSeconds(10);
    private static final int MAX_BINANCE_BOOTSTRAP_MESSAGES = 50_000;
    private static final long MAX_BINANCE_BOOTSTRAP_BYTES = 64L * 1024L * 1024L;

    private final DeepBookSourceDefinition source;
    private final BookPipeline pipeline;
    private final VenueTransport transport;
    private final SnapshotProvider snapshotProvider;
    private final AsyncRawRecorder recorder;
    private final LocalBookPublisher publisher;
    private final PartitionedBookEventDispatcher dispatcher;
    private final long staleThresholdMillis;
    private final SessionHealth health = new SessionHealth();
    private final BookRecoveryPolicy recovery;
    private final StaleWatchdog watchdog;
    private final VenueProtocolStateMachine protocol;
    private final Object lock = new Object();
    private final BoundedBootstrapBuffer<PendingMessage> pendingBinanceUpdates =
            new BoundedBootstrapBuffer<>(
                    MAX_BINANCE_BOOTSTRAP_MESSAGES,
                    MAX_BINANCE_BOOTSTRAP_BYTES,
                    message -> message.payload().length() * Character.BYTES
            );
    private final AtomicLong messages = new AtomicLong();
    private final AtomicLong accepted = new AtomicLong();
    private final AtomicLong snapshots = new AtomicLong();
    private final AtomicLong applied = new AtomicLong();
    private final AtomicLong rejected = new AtomicLong();
    private final AtomicLong staleUpdates = new AtomicLong();
    private final AtomicLong ignored = new AtomicLong();
    private final AtomicLong published = new AtomicLong();
    private final AtomicLong parseNanos = new AtomicLong();
    private final AtomicLong bookNanos = new AtomicLong();

    private volatile WebSocket webSocket;
    private volatile boolean stopped;
    private volatile boolean stopping;
    private volatile boolean binanceSnapshotReady;
    private volatile boolean dataEnabled;
    private volatile long generation;
    private volatile String lastFailure = "";
    private volatile LiveBookSessionFinalState finalState;

    public LiveBookSession(
            DeepBookSourceDefinition source,
            LocalOrderBookBuilder builder,
            HttpClient httpClient,
            ScheduledExecutorService scheduler,
            AsyncRawRecorder recorder,
            LocalBookPublisher publisher,
            Duration staleThreshold
    ) {
        this(source, builder, httpClient, scheduler, recorder, publisher, staleThreshold, null);
    }

    public LiveBookSession(
            DeepBookSourceDefinition source,
            LocalOrderBookBuilder builder,
            HttpClient httpClient,
            ScheduledExecutorService scheduler,
            AsyncRawRecorder recorder,
            LocalBookPublisher publisher,
            Duration staleThreshold,
            PartitionedBookEventDispatcher dispatcher
    ) {
        this(
                source,
                builder,
                new JdkVenueTransport(httpClient),
                new JdkSnapshotProvider(httpClient),
                scheduler,
                recorder,
                publisher,
                staleThreshold,
                dispatcher
        );
    }

    public LiveBookSession(
            DeepBookSourceDefinition source,
            LocalOrderBookBuilder builder,
            VenueTransport transport,
            SnapshotProvider snapshotProvider,
            ScheduledExecutorService scheduler,
            AsyncRawRecorder recorder,
            LocalBookPublisher publisher,
            Duration staleThreshold
    ) {
        this(
                source, builder, transport, snapshotProvider, scheduler,
                recorder, publisher, staleThreshold, null
        );
    }

    public LiveBookSession(
            DeepBookSourceDefinition source,
            LocalOrderBookBuilder builder,
            VenueTransport transport,
            SnapshotProvider snapshotProvider,
            ScheduledExecutorService scheduler,
            AsyncRawRecorder recorder,
            LocalBookPublisher publisher,
            Duration staleThreshold,
            PartitionedBookEventDispatcher dispatcher
    ) {
        if (staleThreshold.isNegative() || staleThreshold.isZero()) {
            throw new IllegalArgumentException("staleThreshold must be positive");
        }
        this.source = source;
        this.pipeline = new BookPipeline(builder, publisher, health);
        this.transport = transport;
        this.snapshotProvider = snapshotProvider;
        this.recorder = recorder;
        this.publisher = publisher;
        this.dispatcher = dispatcher;
        this.staleThresholdMillis = staleThreshold.toMillis();
        this.recovery = new RecoveryCoordinator(
                scheduler,
                health,
                ignoredAttempt -> openConnection(true)
        );
        this.protocol = new VenueSessionProtocol(
                scheduler,
                source,
                staleThreshold,
                this::sendHeartbeat,
                reason -> requestRecovery(reason, generation)
        );
        this.watchdog = new StaleWatchdog(
                scheduler,
                health,
                staleThreshold,
                reason -> {
                    pipeline.availability(generation, BookAvailabilityState.STALE, reason);
                    requestRecovery(reason, generation);
                }
        );
    }
    public void start() {
        openConnection(false);
    }

    private void openConnection(boolean recoveryAttempt) {
        if (stopped) {
            return;
        }
        long nextGeneration;
        synchronized (lock) {
            if (stopped) {
                return;
            }
            generation++;
            nextGeneration = generation;
            pipeline.reset();
            health.bookState(BookState.EMPTY);
            health.connecting(recoveryAttempt);
            binanceSnapshotReady = false;
            dataEnabled = false;
            pendingBinanceUpdates.clear();
            protocol.disconnected();
            WebSocket previous = webSocket;
            webSocket = null;
            if (previous != null) {
                previous.abort();
            }
        }
        if (recoveryAttempt) {
            pipeline.availability(nextGeneration, BookAvailabilityState.RECOVERING,
                    "new recovery generation"
            );
        }
        if (dispatcher != null) {
            dispatcher.resume(source.id());
        }
        record(
                RawRecordType.CONNECT,
                nextGeneration,
                System.currentTimeMillis(),
                System.nanoTime(),
                "",
                recoveryAttempt ? "RECONNECT_ATTEMPT" : "INITIAL_CONNECT"
        );

        transport.connect(source, HTTP_TIMEOUT, new SessionListener(nextGeneration))
                .whenComplete((opened, error) -> {
                    if (error != null && current(nextGeneration)) {
                        String reason = "connect: " + rootMessage(error);
                        lastFailure = reason;
                        health.disconnected(reason);
                        pipeline.availability(nextGeneration,
                                BookAvailabilityState.DISCONNECTED, reason
                        );
                        protocol.disconnected();
                        record(
                                RawRecordType.DISCONNECT,
                                nextGeneration,
                                System.currentTimeMillis(),
                                System.nanoTime(),
                                "",
                                reason
                        );
                        recovery.connectFailed(reason);
                    }
                });
    }

    private void loadBinanceSnapshot(long listenerGeneration) {
        snapshotProvider.load(source, HTTP_TIMEOUT)
                .whenComplete((response, error) -> {
                    if (error != null) {
                        requestRecovery("snapshot: " + rootMessage(error), listenerGeneration);
                        return;
                    }
                    if (!response.successful()) {
                        requestRecovery(
                                "snapshot HTTP " + response.statusCode(),
                                listenerGeneration
                        );
                        return;
                    }
                    long receivedEpochMillis = System.currentTimeMillis();
                    long receivedNanos = System.nanoTime();
                    synchronized (lock) {
                        if (!current(listenerGeneration) || !dataEnabled) {
                            return;
                        }
                        record(
                                RawRecordType.REST_SNAPSHOT,
                                listenerGeneration,
                                receivedEpochMillis,
                                receivedNanos,
                                response.body(),
                                "BEFORE_APPLY"
                        );
                        BookUpdateResult result = pipeline.loadSnapshot(
                                response.body(),
                                receivedEpochMillis
                        );
                        recordResult(result, listenerGeneration, receivedNanos, receivedEpochMillis);
                        record(
                                RawRecordType.REST_SNAPSHOT,
                                listenerGeneration,
                                receivedEpochMillis,
                                System.nanoTime(),
                                response.body(),
                                "AFTER_APPLY status=" + result.status()
                                        + " quality=" + result.quality()
                        );
                        if (result.requiresRecovery()) {
                            pipeline.availability(listenerGeneration, BookAvailabilityState.INVALID,
                                    "invalid Binance snapshot: " + result.detail()
                            );
                            requestRecovery(
                                    "invalid Binance snapshot: " + result.detail(),
                                    listenerGeneration
                            );
                            return;
                        }
                        binanceSnapshotReady = true;
                        while (!pendingBinanceUpdates.isEmpty()) {
                            PendingMessage pending = pendingBinanceUpdates.poll();
                            applyMessage(listenerGeneration, pending);
                            if (!current(listenerGeneration)) {
                                return;
                            }
                        }
                    }
                });
    }

    private void dispatchMessage(
            long listenerGeneration,
            String payload,
            long receivedEpochMillis,
            long receivedNanos
    ) {
        if (stopping) {
            return;
        }
        messages.incrementAndGet();
        RawEnvelope rawMessage = envelope(
                RawRecordType.WS_MESSAGE,
                listenerGeneration,
                receivedEpochMillis,
                receivedNanos,
                payload,
                ""
        );
        recorder.record(rawMessage);
        health.messageReceived(receivedEpochMillis);
        if (dispatcher == null) {
            processMessage(listenerGeneration, payload, receivedEpochMillis, receivedNanos);
            return;
        }
        DispatchResult dispatchResult = dispatcher.submit(
                source.id(),
                () -> processMessage(
                        listenerGeneration,
                        payload,
                        receivedEpochMillis,
                        receivedNanos
                ),
                error -> requestRecovery(
                        "processing task failed: " + rootMessage(error),
                        listenerGeneration
                )
        );
        if (dispatchResult == DispatchResult.FULL) {
            recorder.markReplayUnsafe(rawMessage, "processing queue full");
            rejected.incrementAndGet();
            health.messageReceived(receivedEpochMillis);
            requestRecovery("processing queue full", listenerGeneration);
        }
    }
    private void processMessage(
            long listenerGeneration,
            String payload,
            long receivedEpochMillis,
            long receivedNanos
    ) {
        synchronized (lock) {
            if (!current(listenerGeneration) || !dataEnabled) {
                return;
            }

            ProtocolMessageDecision decision = protocol.onText(payload);
            if (decision.fatal()) {
                rejected.incrementAndGet();
                requestRecovery(decision.detail(), listenerGeneration);
                return;
            }
            if (!decision.bookData()) {
                ignored.incrementAndGet();
                return;
            }
            if (!protocol.bookDataAllowed()) {
                rejected.incrementAndGet();
                requestRecovery("book data arrived before subscription ACK", listenerGeneration);
                return;
            }
            PendingMessage message = new PendingMessage(
                    payload,
                    receivedEpochMillis,
                    receivedNanos
            );
            if ("BINANCE_US".equals(source.exchange()) && !binanceSnapshotReady) {
                if (!pendingBinanceUpdates.offer(message)) {
                    rejected.incrementAndGet();
                    requestRecovery(
                            "Binance bootstrap buffer overflow entries="
                                    + pendingBinanceUpdates.size()
                                    + " bytes=" + pendingBinanceUpdates.bytes(),
                            listenerGeneration
                    );
                }
                return;
            }
            applyMessage(listenerGeneration, message);
        }
    }

    private void applyMessage(long listenerGeneration, PendingMessage message) {
        BookUpdateResult result = pipeline.onMessage(
                message.payload(),
                message.receivedEpochMillis()
        );
        recordResult(
                result,
                listenerGeneration,
                message.receivedNanos(),
                message.receivedEpochMillis()
        );
        if (result.requiresRecovery()) {
            String reason = result.status() + ": " + result.detail();
            pipeline.availability(listenerGeneration, BookAvailabilityState.INVALID, reason
            );
            requestRecovery(reason, listenerGeneration);
        }
    }

    private void recordResult(
            BookUpdateResult result,
            long listenerGeneration,
            long receivedNanos,
            long acceptedEpochMillis
    ) {
        parseNanos.addAndGet(result.parseNanos());
        bookNanos.addAndGet(result.bookNanos());
        health.bookState(BookState.from(result.quality()));

        if (result.accepted()) {
            accepted.incrementAndGet();
            if (result.status() == BookUpdateStatus.SNAPSHOT_LOADED) {
                snapshots.incrementAndGet();
            } else {
                applied.incrementAndGet();
            }
            if (result.quality() == BookQuality.LIVE) {
                health.accepted(acceptedEpochMillis);
                if (pipeline.publishIfEligible(
                        result,
                        listenerGeneration,
                        receivedNanos,
                        acceptedEpochMillis
                )) {
                    published.incrementAndGet();
                    recovery.recoveredLive();
                }
            }
        } else if (result.status() == BookUpdateStatus.STALE) {
            staleUpdates.incrementAndGet();
        } else if (result.status() == BookUpdateStatus.IGNORED) {
            ignored.incrementAndGet();
        } else {
            rejected.incrementAndGet();
            lastFailure = result.status() + ": " + result.detail();
        }
    }

    private void requestRecovery(String reason, long listenerGeneration) {
        synchronized (lock) {
            if (!current(listenerGeneration) || !dataEnabled || recovery.stopped()) {
                return;
            }
            dataEnabled = false;
            if (dispatcher != null) {
                dispatcher.pause(source.id());
            }
        }
        lastFailure = reason;
        pipeline.availability(listenerGeneration, BookAvailabilityState.RECOVERING, reason
        );
        health.recovering(reason);
        protocol.disconnected();
        record(
                RawRecordType.RECOVERY,
                listenerGeneration,
                System.currentTimeMillis(),
                System.nanoTime(),
                "",
                reason
        );
        WebSocket current = webSocket;
        if (current != null) {
            current.abort();
        }
        recovery.requestRecovery(reason);
    }

    public LiveBookSessionSnapshot snapshot() {
        synchronized (lock) {
            long now = System.currentTimeMillis();
            LocalBookSnapshot book = pipeline.snapshot(1);
            SessionHealthSnapshot healthSnapshot = health.snapshot(now, staleThresholdMillis);
            String bid = book.bestBid() == null
                    ? ""
                    : book.bestBid().price().toPlainString();
            String ask = book.bestAsk() == null
                    ? ""
                    : book.bestAsk().price().toPlainString();
            return new LiveBookSessionSnapshot(
                    source.id(),
                    source.exchange(),
                    source.symbol(),
                    generation,
                    healthSnapshot,
                    book.sequence(),
                    messages.get(),
                    accepted.get(),
                    snapshots.get(),
                    applied.get(),
                    rejected.get(),
                    staleUpdates.get(),
                    ignored.get(),
                    published.get(),
                    averageMicros(parseNanos.get(), messages.get() + snapshots.get()),
                    averageMicros(bookNanos.get(), accepted.get() + rejected.get()),
                    recovery.snapshot(),
                    protocol.snapshot(),
                    pendingBinanceUpdates.size(),
                    pendingBinanceUpdates.bytes(),
                    pendingBinanceUpdates.overflows(),
                    bid,
                    ask,
                    lastFailure
            );
        }
    }

    public LocalBookSnapshot bookSnapshot(int levels) {
        synchronized (lock) {
            return pipeline.snapshot(levels);
        }
    }

    public boolean isPublishable() {
        return health.publishable(System.currentTimeMillis(), staleThresholdMillis);
    }

    public LiveBookSessionFinalState stopAndSnapshot(int levels) {
        synchronized (lock) {
            if (finalState != null) {
                return finalState;
            }
            stopping = true;
        }
        if (dispatcher != null) {
            dispatcher.pause(source.id());
            try {
                if (!dispatcher.awaitSourceDrained(source.id(), DISPATCH_DRAIN_TIMEOUT)) {
                    lastFailure = "processing queue did not drain before stop";
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                lastFailure = "interrupted while draining processing queue";
            }
        }

        LiveBookSessionFinalState captured;
        long stoppedGeneration;
        synchronized (lock) {
            if (finalState != null) {
                return finalState;
            }
            stopped = true;
            dataEnabled = false;
            stoppedGeneration = generation;
            captured = new LiveBookSessionFinalState(
                    snapshot(),
                    pipeline.snapshot(levels)
            );
            generation++;
            WebSocket current = webSocket;
            webSocket = null;
            if (current != null) {
                current.abort();
            }
            health.stopped();
            pipeline.availability(stoppedGeneration, BookAvailabilityState.STOPPED,
                    "session stopped"
            );
            finalState = captured;
        }
        watchdog.close();
        protocol.close();
        recovery.close();
        record(
                RawRecordType.DISCONNECT,
                stoppedGeneration,
                System.currentTimeMillis(),
                System.nanoTime(),
                "",
                "STOPPED"
        );
        return captured;
    }

    @Override
    public void close() {
        stopAndSnapshot(1);
    }

    private boolean current(long listenerGeneration) {
        return !stopped && listenerGeneration == generation;
    }

    private void record(
            RawRecordType type,
            long recordGeneration,
            long receivedEpochMillis,
            long receivedNanos,
            String payload,
            String detail
    ) {
        recorder.record(envelope(
                type,
                recordGeneration,
                receivedEpochMillis,
                receivedNanos,
                payload,
                detail
        ));
    }

    private RawEnvelope envelope(
            RawRecordType type,
            long recordGeneration,
            long receivedEpochMillis,
            long receivedNanos,
            String payload,
            String detail
    ) {
        return new RawEnvelope(
                DataSourceModuleVersion.VERSION,
                type,
                recordGeneration,
                source.id(),
                source.exchange(),
                source.symbol(),
                receivedEpochMillis,
                receivedNanos,
                payload,
                detail
        );
    }

    private void sendHeartbeat(long heartbeatGeneration) {
        WebSocket currentSocket;
        synchronized (lock) {
            if (!current(heartbeatGeneration) || !dataEnabled || webSocket == null) {
                return;
            }
            currentSocket = webSocket;
        }
        CompletionStage<WebSocket> send = switch (source.exchange()) {
            case "OKX" -> currentSocket.sendText("ping", true);
            case "KRAKEN" -> currentSocket.sendText(
                    "{\"method\":\"ping\",\"req_id\":" + heartbeatGeneration + "}",
                    true
            );
            case "BINANCE_US" -> currentSocket.sendPing(ByteBuffer.wrap(
                    Long.toString(heartbeatGeneration).getBytes(StandardCharsets.US_ASCII)));
            default -> throw new IllegalArgumentException(
                    "unsupported heartbeat venue " + source.exchange());
        };
        monitorSend(send, heartbeatGeneration, "heartbeat");
    }

    private void monitorSend(
            CompletionStage<WebSocket> send,
            long sendGeneration,
            String operation
    ) {
        send.whenComplete((ignoredSocket, error) -> {
            if (error != null) {
                requestRecovery(
                        operation + " send failed: " + rootMessage(error),
                        sendGeneration
                );
            }
        });
    }
    private static double averageMicros(long totalNanos, long count) {
        return count == 0L ? 0.0 : (double) totalNanos / count / 1_000.0;
    }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return current.getClass().getSimpleName() + (message == null ? "" : ": " + message);
    }

    private record PendingMessage(
            String payload,
            long receivedEpochMillis,
            long receivedNanos
    ) {
    }

    private final class SessionListener implements WebSocket.Listener {
        private final long listenerGeneration;
        private final StringBuilder buffer = new StringBuilder();

        private SessionListener(long listenerGeneration) {
            this.listenerGeneration = listenerGeneration;
        }

        @Override
        public void onOpen(WebSocket openedWebSocket) {
            if (!current(listenerGeneration)) {
                openedWebSocket.abort();
                return;
            }
            synchronized (lock) {
                if (!current(listenerGeneration)) {
                    openedWebSocket.abort();
                    return;
                }
                webSocket = openedWebSocket;
                dataEnabled = true;
                protocol.connected(listenerGeneration);
                health.connected(System.currentTimeMillis());
                recovery.connectEstablished();
                record(
                        RawRecordType.CONNECT,
                        listenerGeneration,
                        System.currentTimeMillis(),
                        System.nanoTime(),
                        "",
                        "CONNECTED"
                );
            }
            if (source.hasSubscribeMessage()) {
                monitorSend(
                        openedWebSocket.sendText(source.subscribeMessage(), true),
                        listenerGeneration,
                        "subscription"
                );
            }
            if ("BINANCE_US".equals(source.exchange())) {
                loadBinanceSnapshot(listenerGeneration);
            }
            openedWebSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(
                WebSocket currentWebSocket,
                CharSequence data,
                boolean last
        ) {
            buffer.append(data);
            if (last) {
                String payload = buffer.toString();
                buffer.setLength(0);
                dispatchMessage(
                        listenerGeneration,
                        payload,
                        System.currentTimeMillis(),
                        System.nanoTime()
                );
            }
            currentWebSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onPing(
                WebSocket currentWebSocket,
                ByteBuffer message
        ) {
            if (current(listenerGeneration)) {
                long now = System.currentTimeMillis();
                health.messageReceived(now);
                protocol.onProtocolPing();
                record(
                        RawRecordType.WS_MESSAGE,
                        listenerGeneration,
                        now,
                        System.nanoTime(),
                        "",
                        "CONTROL PING_FRAME"
                );
            }
            CompletionStage<WebSocket> pong = currentWebSocket.sendPong(message);
            currentWebSocket.request(1);
            return pong;
        }

        @Override
        public CompletionStage<?> onPong(
                WebSocket currentWebSocket,
                ByteBuffer message
        ) {
            if (current(listenerGeneration)) {
                long now = System.currentTimeMillis();
                health.messageReceived(now);
                protocol.onProtocolPong();
                record(
                        RawRecordType.WS_MESSAGE,
                        listenerGeneration,
                        now,
                        System.nanoTime(),
                        "",
                        "CONTROL PONG_FRAME"
                );
            }
            currentWebSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onBinary(
                WebSocket currentWebSocket,
                ByteBuffer data,
                boolean last
        ) {
            if (current(listenerGeneration)) {
                requestRecovery(
                        "unexpected binary WebSocket frame bytes=" + data.remaining(),
                        listenerGeneration
                );
            }
            currentWebSocket.request(1);
            return null;
        }
        @Override
        public CompletionStage<?> onClose(
                WebSocket currentWebSocket,
                int statusCode,
                String reason
        ) {
            if (current(listenerGeneration)) {
                String detail = "close " + statusCode + ": " + reason;
                health.disconnected(detail);
                pipeline.availability(listenerGeneration,
                        BookAvailabilityState.DISCONNECTED, detail
                );
                protocol.disconnected();
                record(
                        RawRecordType.DISCONNECT,
                        listenerGeneration,
                        System.currentTimeMillis(),
                        System.nanoTime(),
                        "",
                        detail
                );
                requestRecovery(detail, listenerGeneration);
            }
            return null;
        }

        @Override
        public void onError(WebSocket currentWebSocket, Throwable error) {
            if (current(listenerGeneration)) {
                String detail = "websocket: " + rootMessage(error);
                health.disconnected(detail);
                pipeline.availability(listenerGeneration,
                        BookAvailabilityState.INVALID, detail
                );
                protocol.disconnected();
                record(
                        RawRecordType.DISCONNECT,
                        listenerGeneration,
                        System.currentTimeMillis(),
                        System.nanoTime(),
                        "",
                        detail
                );
                requestRecovery(detail, listenerGeneration);
            }
        }
    }
}

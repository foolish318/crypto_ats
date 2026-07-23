package com.example.hft.datasource.deepbook.runtime;

import com.example.hft.datasource.DataSourceModuleVersion;
import com.example.hft.datasource.book.BookQuality;
import com.example.hft.datasource.deepbook.DeepBookSourceDefinition;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicLong;


public final class LiveBookSession implements AutoCloseable {
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(10);

    private final DeepBookSourceDefinition source;
    private final LocalOrderBookBuilder builder;
    private final HttpClient httpClient;
    private final AsyncRawRecorder recorder;
    private final LocalBookPublisher publisher;
    private final long staleThresholdMillis;
    private final SessionHealth health = new SessionHealth();
    private final RecoveryCoordinator recovery;
    private final StaleWatchdog watchdog;
    private final Object lock = new Object();
    private final Queue<PendingMessage> pendingBinanceUpdates = new ArrayDeque<>();
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
        if (staleThreshold.isNegative() || staleThreshold.isZero()) {
            throw new IllegalArgumentException("staleThreshold must be positive");
        }
        this.source = source;
        this.builder = builder;
        this.httpClient = httpClient;
        this.recorder = recorder;
        this.publisher = publisher;
        this.staleThresholdMillis = staleThreshold.toMillis();
        this.recovery = new RecoveryCoordinator(scheduler, health, ignoredAttempt -> openConnection(true));
        this.watchdog = new StaleWatchdog(
                scheduler,
                health,
                staleThreshold,
                reason -> requestRecovery(reason, generation)
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
            builder.reset();
            health.bookState(BookState.EMPTY);
            health.connecting(recoveryAttempt);
            binanceSnapshotReady = false;
            dataEnabled = false;
            pendingBinanceUpdates.clear();
            WebSocket previous = webSocket;
            webSocket = null;
            if (previous != null) {
                previous.abort();
            }
        }
        record(
                RawRecordType.CONNECT,
                nextGeneration,
                System.currentTimeMillis(),
                System.nanoTime(),
                "",
                recoveryAttempt ? "RECONNECT_ATTEMPT" : "INITIAL_CONNECT"
        );

        httpClient.newWebSocketBuilder()
                .header("User-Agent", "hft-java-learning/0.1")
                .connectTimeout(HTTP_TIMEOUT)
                .buildAsync(source.webSocketUri(), new SessionListener(nextGeneration))
                .whenComplete((opened, error) -> {
                    if (error != null && current(nextGeneration)) {
                        String reason = "connect: " + rootMessage(error);
                        lastFailure = reason;
                        health.disconnected(reason);
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
        HttpRequest request = HttpRequest.newBuilder(source.snapshotUri())
                .timeout(HTTP_TIMEOUT)
                .GET()
                .build();
        httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .whenComplete((response, error) -> {
                    if (error != null) {
                        requestRecovery("snapshot: " + rootMessage(error), listenerGeneration);
                        return;
                    }
                    if (response.statusCode() / 100 != 2) {
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
                        BookUpdateResult result = builder.loadSnapshot(
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
                            requestRecovery(
                                    "invalid Binance snapshot: " + result.detail(),
                                    listenerGeneration
                            );
                            return;
                        }
                        binanceSnapshotReady = true;
                        while (!pendingBinanceUpdates.isEmpty()) {
                            PendingMessage pending = pendingBinanceUpdates.remove();
                            applyMessage(listenerGeneration, pending);
                            if (!current(listenerGeneration)) {
                                return;
                            }
                        }
                    }
                });
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
            messages.incrementAndGet();
            record(
                    RawRecordType.WS_MESSAGE,
                    listenerGeneration,
                    receivedEpochMillis,
                    receivedNanos,
                    payload,
                    ""
            );
            health.messageReceived(receivedEpochMillis);
            PendingMessage message = new PendingMessage(
                    payload,
                    receivedEpochMillis,
                    receivedNanos
            );
            if ("BINANCE_US".equals(source.exchange()) && !binanceSnapshotReady) {
                pendingBinanceUpdates.add(message);
                return;
            }
            applyMessage(listenerGeneration, message);
        }
    }

    private void applyMessage(long listenerGeneration, PendingMessage message) {
        BookUpdateResult result = builder.onMessage(
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
            requestRecovery(result.status() + ": " + result.detail(), listenerGeneration);
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
                if (publisher.publishIfEligible(
                        builder,
                        result,
                        health,
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
        }
        lastFailure = reason;
        health.recovering(reason);
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
            LocalBookSnapshot book = builder.snapshot(1);
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
                    bid,
                    ask,
                    lastFailure
            );
        }
    }

    public LocalBookSnapshot bookSnapshot(int levels) {
        synchronized (lock) {
            return builder.snapshot(levels);
        }
    }

    public boolean isPublishable() {
        return health.publishable(System.currentTimeMillis(), staleThresholdMillis);
    }

    public LiveBookSessionFinalState stopAndSnapshot(int levels) {
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
                    builder.snapshot(levels)
            );
            generation++;
            WebSocket current = webSocket;
            webSocket = null;
            if (current != null) {
                current.abort();
            }
            health.stopped();
            finalState = captured;
        }
        watchdog.close();
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
        recorder.record(new RawEnvelope(
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
        ));
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
                openedWebSocket.sendText(source.subscribeMessage(), true);
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
                processMessage(
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
        public CompletionStage<?> onClose(
                WebSocket currentWebSocket,
                int statusCode,
                String reason
        ) {
            if (current(listenerGeneration)) {
                String detail = "close " + statusCode + ": " + reason;
                health.disconnected(detail);
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

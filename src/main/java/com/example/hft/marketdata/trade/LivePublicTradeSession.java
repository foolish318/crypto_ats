package com.example.hft.marketdata.trade;

import com.example.hft.datasource.DataSourceModuleVersion;
import com.example.hft.datasource.deepbook.runtime.AsyncRawRecorder;
import com.example.hft.datasource.deepbook.runtime.RawEnvelope;
import com.example.hft.datasource.deepbook.runtime.RawRecordType;
import com.example.hft.datasource.deepbook.runtime.RecoveryCoordinator;
import com.example.hft.datasource.deepbook.runtime.SessionHealth;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public final class LivePublicTradeSession implements AutoCloseable {
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

    private final PublicTradeSourceDefinition source;
    private final PublicTradePipeline pipeline;
    private final HttpClient httpClient;
    private final ScheduledExecutorService scheduler;
    private final AsyncRawRecorder rawRecorder;
    private final ObjectMapper mapper;
    private final SessionHealth health = new SessionHealth();
    private final RecoveryCoordinator recovery;
    private final Object lock = new Object();
    private final AtomicLong rawMessages = new AtomicLong();

    private volatile WebSocket webSocket;
    private volatile ScheduledFuture<?> heartbeat;
    private volatile boolean stopped;
    private volatile boolean dataEnabled;
    private volatile boolean subscribed;
    private volatile long streamEpoch;
    private volatile String lastFailure = "";

    public LivePublicTradeSession(
            PublicTradeSourceDefinition source,
            PublicTradePipeline pipeline,
            HttpClient httpClient,
            ScheduledExecutorService scheduler,
            AsyncRawRecorder rawRecorder,
            ObjectMapper mapper
    ) {
        if (source == null || pipeline == null || httpClient == null
                || scheduler == null || rawRecorder == null || mapper == null) {
            throw new IllegalArgumentException("trade session dependencies are required");
        }
        this.source = source;
        this.pipeline = pipeline;
        this.httpClient = httpClient;
        this.scheduler = scheduler;
        this.rawRecorder = rawRecorder;
        this.mapper = mapper;
        this.recovery = new RecoveryCoordinator(
                scheduler,
                health,
                ignored -> openConnection(true)
        );
    }

    public void start() {
        openConnection(false);
    }

    private void openConnection(boolean recovering) {
        long nextEpoch;
        synchronized (lock) {
            if (stopped) {
                return;
            }
            streamEpoch++;
            nextEpoch = streamEpoch;
            dataEnabled = false;
            subscribed = false;
            health.connecting(recovering);
            cancelHeartbeat();
            WebSocket previous = webSocket;
            webSocket = null;
            if (previous != null) {
                previous.abort();
            }
        }
        record(RawRecordType.CONNECT, nextEpoch, "",
                recovering ? "TRADE_RECONNECT_ATTEMPT" : "TRADE_INITIAL_CONNECT");
        httpClient.newWebSocketBuilder()
                .header("User-Agent", "crypto-ats-market-data/1.1")
                .connectTimeout(CONNECT_TIMEOUT)
                .buildAsync(source.webSocketUri(), new Listener(nextEpoch))
                .whenComplete((socket, error) -> {
                    if (error != null && current(nextEpoch)) {
                        String reason = "trade connect: " + rootMessage(error);
                        lastFailure = reason;
                        health.disconnected(reason);
                        record(RawRecordType.DISCONNECT, nextEpoch, "", reason);
                        recovery.connectFailed(reason);
                    }
                });
    }

    private void onPayload(long epoch, String payload, long receiveEpoch, long receiveNanos) {
        if (!current(epoch) || !dataEnabled) {
            return;
        }
        rawMessages.incrementAndGet();
        if (!rawRecorder.record(new RawEnvelope(
                DataSourceModuleVersion.VERSION,
                RawRecordType.WS_MESSAGE,
                epoch,
                source.id(),
                source.venue().name(),
                source.venueSymbol(),
                receiveEpoch,
                receiveNanos,
                payload,
                "PUBLIC_TRADE"
        ))) {
            requestRecovery("raw recorder rejected public trade message", epoch);
            return;
        }
        try {
            JsonNode root = "pong".equals(payload) ? null : mapper.readTree(payload);
            if (protocolError(root)) {
                requestRecovery("public trade subscription/protocol error: " + payload, epoch);
                return;
            }
            if (subscriptionAck(root)) {
                subscribed = true;
                recovery.recoveredLive();
                return;
            }
            if (control(root, payload)) {
                return;
            }
            if (!subscribed) {
                requestRecovery("public trade data before subscription ACK", epoch);
                return;
            }
            TradeProcessingResult result = pipeline.onMessage(
                    payload, epoch, receiveEpoch, receiveNanos
            );
            if (result.invalid() > 0) {
                requestRecovery("invalid public trade payload: " + result.detail(), epoch);
            } else if (result.published() > 0) {
                recovery.recoveredLive();
            }
        } catch (Exception error) {
            requestRecovery("public trade protocol parse: " + rootMessage(error), epoch);
        }
    }

    private boolean subscriptionAck(JsonNode root) {
        if (root == null) {
            return false;
        }
        return switch (source.venue()) {
            case BINANCE_US -> false;
            case OKX -> "subscribe".equals(root.path("event").asText())
                    && "0".equals(root.path("code").asText("0"));
            case KRAKEN -> "subscribe".equals(root.path("method").asText())
                    && root.path("success").asBoolean(false);
        };
    }

    private boolean protocolError(JsonNode root) {
        if (root == null) {
            return false;
        }
        return root.has("error")
                || "error".equals(root.path("event").asText())
                || root.has("code") && !"0".equals(root.path("code").asText("0"))
                || root.path("success").isBoolean() && !root.path("success").asBoolean();
    }

    private boolean control(JsonNode root, String payload) {
        if ("pong".equals(payload) || root == null) {
            return true;
        }
        return "heartbeat".equals(root.path("channel").asText())
                || "pong".equals(root.path("method").asText());
    }

    private void requestRecovery(String reason, long epoch) {
        synchronized (lock) {
            if (!current(epoch) || !dataEnabled || recovery.stopped()) {
                return;
            }
            dataEnabled = false;
            subscribed = false;
            cancelHeartbeat();
        }
        lastFailure = reason;
        health.recovering(reason);
        record(RawRecordType.RECOVERY, epoch, "", reason);
        WebSocket current = webSocket;
        if (current != null) {
            current.abort();
        }
        recovery.requestRecovery(reason);
    }

    private void scheduleHeartbeat(long epoch) {
        cancelHeartbeat();
        heartbeat = scheduler.scheduleAtFixedRate(
                () -> sendHeartbeat(epoch), 10L, 10L, TimeUnit.SECONDS
        );
    }

    private void sendHeartbeat(long epoch) {
        WebSocket socket = webSocket;
        if (!current(epoch) || !dataEnabled || socket == null) {
            return;
        }
        CompletionStage<WebSocket> sent = switch (source.venue()) {
            case BINANCE_US -> socket.sendPing(ByteBuffer.wrap(
                    Long.toString(epoch).getBytes(StandardCharsets.US_ASCII)));
            case OKX -> socket.sendText("ping", true);
            case KRAKEN -> socket.sendText(
                    "{\"method\":\"ping\",\"req_id\":" + epoch + "}", true);
        };
        sent.whenComplete((ignored, error) -> {
            if (error != null) {
                requestRecovery("public trade heartbeat: " + rootMessage(error), epoch);
            }
        });
    }

    private void cancelHeartbeat() {
        ScheduledFuture<?> current = heartbeat;
        heartbeat = null;
        if (current != null) {
            current.cancel(false);
        }
    }

    public PublicTradeSessionSnapshot snapshot() {
        return new PublicTradeSessionSnapshot(
                source.id(), source.venue(), source.venueSymbol(), streamEpoch,
                rawMessages.get(), pipeline.normalized(), pipeline.published(),
                pipeline.duplicates(), pipeline.outOfOrder(), pipeline.invalid(),
                recovery.snapshot(), lastFailure
        );
    }

    @Override
    public void close() {
        long stoppedEpoch;
        synchronized (lock) {
            if (stopped) {
                return;
            }
            stopped = true;
            dataEnabled = false;
            subscribed = false;
            stoppedEpoch = streamEpoch;
            streamEpoch++;
            cancelHeartbeat();
            WebSocket current = webSocket;
            webSocket = null;
            if (current != null) {
                current.abort();
            }
            health.stopped();
        }
        recovery.close();
        record(RawRecordType.DISCONNECT, stoppedEpoch, "", "TRADE_STOPPED");
    }

    private boolean current(long epoch) {
        return !stopped && epoch == streamEpoch;
    }

    private void record(RawRecordType type, long epoch, String payload, String detail) {
        rawRecorder.record(new RawEnvelope(
                DataSourceModuleVersion.VERSION,
                type,
                epoch,
                source.id(),
                source.venue().name(),
                source.venueSymbol(),
                System.currentTimeMillis(),
                System.nanoTime(),
                payload,
                detail
        ));
    }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getClass().getSimpleName()
                + (current.getMessage() == null ? "" : ": " + current.getMessage());
    }

    private final class Listener implements WebSocket.Listener {
        private final long epoch;
        private final StringBuilder text = new StringBuilder();

        private Listener(long epoch) {
            this.epoch = epoch;
        }

        @Override
        public void onOpen(WebSocket socket) {
            if (!current(epoch)) {
                socket.abort();
                return;
            }
            synchronized (lock) {
                if (!current(epoch)) {
                    socket.abort();
                    return;
                }
                webSocket = socket;
                dataEnabled = true;
                subscribed = !source.hasSubscribeMessage();
                health.connected(System.currentTimeMillis());
                recovery.connectEstablished();
                record(RawRecordType.CONNECT, epoch, "", "TRADE_CONNECTED");
                scheduleHeartbeat(epoch);
            }
            if (source.hasSubscribeMessage()) {
                socket.sendText(source.subscribeMessage(), true)
                        .whenComplete((ignored, error) -> {
                            if (error != null) {
                                requestRecovery(
                                        "public trade subscribe: " + rootMessage(error), epoch);
                            }
                        });
            }
            socket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket socket, CharSequence data, boolean last) {
            text.append(data);
            if (last) {
                String payload = text.toString();
                text.setLength(0);
                onPayload(epoch, payload, System.currentTimeMillis(), System.nanoTime());
            }
            socket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onPing(WebSocket socket, ByteBuffer message) {
            CompletionStage<WebSocket> pong = socket.sendPong(message);
            socket.request(1);
            return pong;
        }

        @Override
        public CompletionStage<?> onPong(WebSocket socket, ByteBuffer message) {
            socket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket socket, int statusCode, String reason) {
            if (current(epoch)) {
                String detail = "public trade close " + statusCode + ": " + reason;
                health.disconnected(detail);
                record(RawRecordType.DISCONNECT, epoch, "", detail);
                requestRecovery(detail, epoch);
            }
            return null;
        }

        @Override
        public void onError(WebSocket socket, Throwable error) {
            if (current(epoch)) {
                String detail = "public trade websocket: " + rootMessage(error);
                health.disconnected(detail);
                record(RawRecordType.DISCONNECT, epoch, "", detail);
                requestRecovery(detail, epoch);
            }
        }
    }
}
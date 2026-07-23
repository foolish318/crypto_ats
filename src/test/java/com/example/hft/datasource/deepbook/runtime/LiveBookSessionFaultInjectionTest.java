package com.example.hft.datasource.deepbook.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.hft.datasource.deepbook.DeepBookSourceCatalog;
import com.example.hft.datasource.deepbook.DeepBookSourceDefinition;
import com.example.hft.datasource.engine.MarketDataCache;
import com.example.hft.datasource.engine.MarketDataEngine;
import com.example.hft.datasource.engine.MarketDataEventBus;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;


class LiveBookSessionFaultInjectionTest {
    @TempDir
    Path tempDir;

    @Test
    void fragmentedMessageIsAppliedThroughInjectedTransport() throws Exception {
        try (Fixture fixture = Fixture.okx(tempDir.resolve("fragmented.jsonl"))) {
            fixture.session.start();
            FakeConnection connection = fixture.transport.connection(0);
            connection.text("{\"event\":\"subscribe\",\"code\":\"0\"}", true);
            String snapshot = okxSnapshot(10L);
            int split = snapshot.length() / 2;
            connection.text(snapshot.substring(0, split), false);
            connection.text(snapshot.substring(split), true);

            assertEquals(
                    10L,
                    fixture.cache.deepBook("OKX", "BTC-USDT").orElseThrow().sequence()
            );
        }
    }

    @Test
    void binanceBuffersUpdatesUntilDelayedSnapshotCompletes() throws Exception {
        CompletableFuture<SnapshotResponse> delayed = new CompletableFuture<>();
        try (Fixture fixture = Fixture.binance(
                tempDir.resolve("delayed-snapshot.jsonl"),
                (source, timeout) -> delayed
        )) {
            fixture.session.start();
            FakeConnection connection = fixture.transport.connection(0);
            connection.text(binanceUpdate(100L, 101L), true);
            assertTrue(fixture.cache.deepBook("BINANCE_US", "BTCUSDT").isEmpty());

            delayed.complete(new SnapshotResponse(200, binanceSnapshot(100L)));

            waitUntil(() -> fixture.cache.deepBook("BINANCE_US", "BTCUSDT").isPresent());
            assertEquals(
                    101L,
                    fixture.cache.deepBook("BINANCE_US", "BTCUSDT").orElseThrow().sequence()
            );
        }
    }

    @Test
    void disconnectDuringSnapshotPreventsOldSnapshotApplication() throws Exception {
        CompletableFuture<SnapshotResponse> delayed = new CompletableFuture<>();
        try (Fixture fixture = Fixture.binance(
                tempDir.resolve("snapshot-disconnect.jsonl"),
                (source, timeout) -> delayed
        )) {
            fixture.session.start();
            FakeConnection connection = fixture.transport.connection(0);
            connection.text(binanceUpdate(100L, 101L), true);
            connection.closeFromVenue();
            delayed.complete(new SnapshotResponse(200, binanceSnapshot(100L)));

            Thread.sleep(50L);
            assertTrue(fixture.cache.deepBook("BINANCE_US", "BTCUSDT").isEmpty());
        }
    }

    @Test
    void secondDisconnectBeforeRecoveryCompletesStartsAnotherGeneration() throws Exception {
        try (Fixture fixture = Fixture.okx(tempDir.resolve("recovery-disconnect.jsonl"))) {
            fixture.session.start();
            fixture.transport.connection(0).closeFromVenue();

            waitUntil(() -> fixture.transport.connectionCount() >= 2);
            fixture.transport.connection(1).closeFromVenue();

            waitUntil(() -> fixture.transport.connectionCount() >= 3);
            assertEquals(3L, fixture.session.snapshot().generation());
        }
    }

    @Test
    void lateOldGenerationCallbackCannotPolluteRecoveredBook() throws Exception {
        try (Fixture fixture = Fixture.okx(tempDir.resolve("old-generation.jsonl"))) {
            fixture.session.start();
            FakeConnection old = fixture.transport.connection(0);
            old.text("{\"event\":\"subscribe\",\"code\":\"0\"}", true);
            old.text(okxSnapshot(10L), true);
            old.closeFromVenue();

            waitUntil(() -> fixture.transport.connectionCount() >= 2);
            FakeConnection recovered = fixture.transport.connection(1);
            recovered.text("{\"event\":\"subscribe\",\"code\":\"0\"}", true);
            recovered.text(okxSnapshot(20L), true);
            old.text(okxUpdate(10L, 11L), true);

            AcceptedLocalBookEvent current =
                    fixture.cache.deepBook("OKX", "BTC-USDT").orElseThrow();
            assertEquals(2L, current.generation());
            assertEquals(20L, current.sequence());
        }
    }

    @Test
    void shutdownDuringRecoveryCancelsFutureConnect() throws Exception {
        Fixture fixture = Fixture.okx(tempDir.resolve("shutdown-recovery.jsonl"));
        fixture.session.start();
        fixture.transport.connection(0).closeFromVenue();
        fixture.session.close();
        Thread.sleep(500L);

        assertEquals(1, fixture.transport.connectionCount());
        fixture.closeResources();
    }

    private static String okxSnapshot(long sequence) {
        long now = System.currentTimeMillis();
        return "{\"arg\":{\"channel\":\"books\",\"instId\":\"BTC-USDT\"},"
                + "\"action\":\"snapshot\",\"data\":[{"
                + "\"bids\":[[\"100.0\",\"1.0\",\"0\",\"1\"]],"
                + "\"asks\":[[\"101.0\",\"2.0\",\"0\",\"1\"]],"
                + "\"ts\":\"" + now + "\",\"seqId\":" + sequence
                + ",\"prevSeqId\":-1}]}";
    }

    private static String okxUpdate(long previous, long sequence) {
        long now = System.currentTimeMillis();
        return "{\"arg\":{\"channel\":\"books\",\"instId\":\"BTC-USDT\"},"
                + "\"action\":\"update\",\"data\":[{"
                + "\"bids\":[[\"100.0\",\"1.1\",\"0\",\"1\"]],\"asks\":[],"
                + "\"ts\":\"" + now + "\",\"seqId\":" + sequence
                + ",\"prevSeqId\":" + previous + "}]}";
    }

    private static String binanceSnapshot(long lastUpdateId) {
        return "{\"lastUpdateId\":" + lastUpdateId
                + ",\"bids\":[[\"100.0\",\"1.0\"]],"
                + "\"asks\":[[\"101.0\",\"2.0\"]]}";
    }

    private static String binanceUpdate(long first, long last) {
        long now = System.currentTimeMillis();
        return "{\"e\":\"depthUpdate\",\"E\":" + now
                + ",\"s\":\"BTCUSDT\",\"U\":" + first + ",\"u\":" + last
                + ",\"b\":[[\"100.0\",\"1.1\"]],\"a\":[]}";
    }

    private static void waitUntil(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(3).toNanos();
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(10L);
        }
        assertTrue(condition.getAsBoolean(), "condition did not become true");
    }

    private static final class Fixture implements AutoCloseable {
        private final ScheduledExecutorService scheduler =
                Executors.newScheduledThreadPool(2);
        private final FakeVenueTransport transport = new FakeVenueTransport();
        private final MarketDataCache cache = new MarketDataCache();
        private final MarketDataEventBus bus = new MarketDataEventBus();
        private final AsyncRawRecorder recorder;
        private final LiveBookSession session;
        private boolean resourcesClosed;

        private Fixture(
                Path journal,
                DeepBookSourceDefinition source,
                SnapshotProvider snapshotProvider
        ) throws Exception {
            recorder = new AsyncRawRecorder(
                    journal,
                    new ObjectMapper(),
                    64,
                    new RawJournalConfig(
                            1_000_000L,
                            Duration.ofMinutes(1),
                            Duration.ofHours(1),
                            0L,
                            1,
                            32,
                            source.id()
                    )
            );
            MarketDataEngine engine = new MarketDataEngine(cache, bus);
            LocalBookPublisher publisher = new LocalBookPublisher(engine, 5_000L, 10);
            session = new LiveBookSession(
                    source,
                    LocalOrderBookBuilderFactory.create(source),
                    transport,
                    snapshotProvider,
                    scheduler,
                    recorder,
                    publisher,
                    Duration.ofSeconds(2)
            );
        }

        private static Fixture okx(Path path) throws Exception {
            return new Fixture(
                    path,
                    DeepBookSourceCatalog.okx("BTC-USDT"),
                    (source, timeout) -> CompletableFuture.failedFuture(
                            new AssertionError("OKX must not request REST snapshot"))
            );
        }

        private static Fixture binance(Path path, SnapshotProvider provider) throws Exception {
            return new Fixture(path, DeepBookSourceCatalog.binanceUs("BTCUSDT"), provider);
        }

        @Override
        public void close() throws Exception {
            session.close();
            closeResources();
        }

        private void closeResources() throws Exception {
            if (resourcesClosed) {
                return;
            }
            resourcesClosed = true;
            bus.close();
            recorder.close();
            scheduler.shutdownNow();
        }
    }

    private static final class FakeVenueTransport implements VenueTransport {
        private final List<FakeConnection> connections = new ArrayList<>();

        @Override
        public synchronized CompletionStage<WebSocket> connect(
                DeepBookSourceDefinition source,
                Duration timeout,
                WebSocket.Listener listener
        ) {
            FakeConnection connection = new FakeConnection(listener);
            connections.add(connection);
            listener.onOpen(connection.socket);
            return CompletableFuture.completedFuture(connection.socket);
        }

        private synchronized FakeConnection connection(int index) {
            return connections.get(index);
        }

        private synchronized int connectionCount() {
            return connections.size();
        }
    }

    private static final class FakeConnection {
        private final WebSocket.Listener listener;
        private final FakeWebSocket socket = new FakeWebSocket();

        private FakeConnection(WebSocket.Listener listener) {
            this.listener = listener;
        }

        private void text(String value, boolean last) {
            listener.onText(socket, value, last);
        }

        private void closeFromVenue() {
            listener.onClose(socket, 1006, "injected disconnect");
        }
    }

    private static final class FakeWebSocket implements WebSocket {
        private volatile boolean aborted;

        @Override
        public CompletableFuture<WebSocket> sendText(CharSequence data, boolean last) {
            return CompletableFuture.completedFuture(this);
        }

        @Override
        public CompletableFuture<WebSocket> sendBinary(ByteBuffer data, boolean last) {
            return CompletableFuture.completedFuture(this);
        }

        @Override
        public CompletableFuture<WebSocket> sendPing(ByteBuffer message) {
            return CompletableFuture.completedFuture(this);
        }

        @Override
        public CompletableFuture<WebSocket> sendPong(ByteBuffer message) {
            return CompletableFuture.completedFuture(this);
        }

        @Override
        public CompletableFuture<WebSocket> sendClose(int statusCode, String reason) {
            return CompletableFuture.completedFuture(this);
        }

        @Override
        public void request(long n) {
        }

        @Override
        public String getSubprotocol() {
            return "";
        }

        @Override
        public boolean isOutputClosed() {
            return aborted;
        }

        @Override
        public boolean isInputClosed() {
            return aborted;
        }

        @Override
        public void abort() {
            aborted = true;
        }
    }
}

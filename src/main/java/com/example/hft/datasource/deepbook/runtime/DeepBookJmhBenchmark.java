package com.example.hft.datasource.deepbook.runtime;

import com.example.hft.datasource.book.BookQuality;
import com.example.hft.datasource.deepbook.DeepBookSourceCatalog;
import com.example.hft.datasource.deepbook.DeepBookSourceDefinition;
import com.example.hft.datasource.engine.MarketDataCache;
import com.example.hft.datasource.engine.MarketDataEngine;
import com.example.hft.datasource.engine.MarketDataEventBus;
import com.example.hft.datasource.transport.TransportType;
import com.example.hft.marketdata.api.DefaultStrategyMarketDataPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;


@BenchmarkMode({Mode.SampleTime, Mode.Throughput})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class DeepBookJmhBenchmark {
    @Benchmark
    public ProtocolMessageDecision classify(BenchmarkState state) {
        return state.classifier.classify(state.snapshotPayload);
    }

    @Benchmark
    public JsonNode parseJson(BenchmarkState state) throws Exception {
        return state.mapper.readTree(state.snapshotPayload);
    }

    @Benchmark
    public void mutateBook(BenchmarkState state) {
        state.mutableBook.applyArrayUpdates(state.updateBids, state.updateAsks);
    }

    @Benchmark
    public LocalBookSnapshot createSnapshot(BenchmarkState state) {
        return state.builder.snapshot(10);
    }

    @Benchmark
    public boolean publishLocalBook(BenchmarkState state) {
        return state.publisher.publishIfEligible(
                state.builder,
                state.acceptedResult,
                state.health,
                1L,
                System.nanoTime(),
                state.nowMillis
        );
    }

    @Benchmark
    public void updateCacheAndPublishEvent(BenchmarkState state) {
        state.engine.onEvent(state.acceptedEvent);
    }

    @State(Scope.Thread)
    public static class BenchmarkState {
        private final ObjectMapper mapper = new ObjectMapper();
        private final DeepBookSourceDefinition source = DeepBookSourceCatalog.okx("BTC-USDT");
        private final String snapshotPayload = snapshotPayload();
        private final String updatePayload = updatePayload();
        private VenueProtocolMessageClassifier classifier;
        private MutableDecimalOrderBook mutableBook;
        private JsonNode updateBids;
        private JsonNode updateAsks;
        private LocalOrderBookBuilder builder;
        private BookUpdateResult acceptedResult;
        private SessionHealth health;
        private MarketDataEngine engine;
        private LocalBookPublisher publisher;
        private AcceptedLocalBookEvent acceptedEvent;
        private long nowMillis;

        @Setup
        public void setup() throws Exception {
            nowMillis = System.currentTimeMillis();
            classifier = new VenueProtocolMessageClassifier(source, mapper);
            JsonNode snapshot = mapper.readTree(snapshotPayload).path("data").get(0);
            JsonNode update = mapper.readTree(updatePayload).path("data").get(0);
            mutableBook = new MutableDecimalOrderBook();
            mutableBook.loadArraySnapshot(snapshot.path("bids"), snapshot.path("asks"));
            updateBids = update.path("bids");
            updateAsks = update.path("asks");

            builder = LocalOrderBookBuilderFactory.create(source, java.time.Duration.ofDays(36500));
            acceptedResult = builder.onMessage(snapshotPayload, nowMillis);
            health = new SessionHealth();
            health.connecting(false);
            health.connected(nowMillis);
            health.accepted(nowMillis);
            MarketDataCache cache = new MarketDataCache();
            MarketDataEventBus bus = new MarketDataEventBus();
            bus.subscribe(new DefaultStrategyMarketDataPort(() -> nowMillis));
            engine = new MarketDataEngine(cache, bus);
            publisher = new LocalBookPublisher(engine, 60_000L, 10, () -> nowMillis);
            LocalBookSnapshot book = builder.snapshot(10);
            acceptedEvent = new AcceptedLocalBookEvent(
                    source.id(),
                    source.exchange(),
                    source.symbol(),
                    builder.canonicalInstrumentId(),
                    TransportType.REPLAY,
                    System.nanoTime(),
                    book.exchangeTime().toEpochMilli(),
                    book.sequence(),
                    1L,
                    nowMillis,
                    new LocalBookSnapshot(
                            book.sourceId(),
                            book.exchange(),
                            book.symbol(),
                            BookQuality.LIVE,
                            book.sequence(),
                            Instant.ofEpochMilli(nowMillis),
                            book.bids(),
                            book.asks()
                    )
            );
        }

        private static String snapshotPayload() {
            long now = System.currentTimeMillis();
            return "{\"arg\":{\"channel\":\"books\",\"instId\":\"BTC-USDT\"},"
                    + "\"action\":\"snapshot\",\"data\":[{"
                    + "\"bids\":[[\"100.0\",\"1.0\",\"0\",\"1\"],"
                    + "[\"99.0\",\"2.0\",\"0\",\"1\"]],"
                    + "\"asks\":[[\"101.0\",\"1.5\",\"0\",\"1\"],"
                    + "[\"102.0\",\"2.5\",\"0\",\"1\"]],"
                    + "\"ts\":\"" + now + "\",\"seqId\":10,\"prevSeqId\":-1}]}";
        }

        private static String updatePayload() {
            long now = System.currentTimeMillis();
            return "{\"arg\":{\"channel\":\"books\",\"instId\":\"BTC-USDT\"},"
                    + "\"action\":\"update\",\"data\":[{"
                    + "\"bids\":[[\"100.0\",\"1.1\",\"0\",\"1\"]],"
                    + "\"asks\":[[\"101.0\",\"1.6\",\"0\",\"1\"]],"
                    + "\"ts\":\"" + now + "\",\"seqId\":11,\"prevSeqId\":10}]}";
        }
    }
}

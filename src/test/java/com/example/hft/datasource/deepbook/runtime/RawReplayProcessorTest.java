package com.example.hft.datasource.deepbook.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.hft.datasource.DataSourceModuleVersion;
import com.example.hft.datasource.deepbook.DeepBookSourceCatalog;
import com.example.hft.datasource.deepbook.DeepBookSourceDefinition;
import com.example.hft.datasource.deepbook.quality.KrakenBookChecksum;
import com.example.hft.datasource.deepbook.quality.KrakenBookChecksum.ChecksumLevel;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;


class RawReplayProcessorTest {
    private static final long RECEIVED = 3_000L;

    @Test
    void replayRebuildsSameFinalBooksForAllVenues() {
        List<DeepBookSourceDefinition> sources = List.of(
                DeepBookSourceCatalog.binanceUs("BTCUSDT"),
                DeepBookSourceCatalog.okx("BTC-USDT"),
                DeepBookSourceCatalog.kraken("BTC/USD")
        );
        List<RawEnvelope> records = records(sources);
        Map<String, LocalBookSnapshot> live = processLikeLive(sources, records);

        RawReplayResult replay = new RawReplayProcessor(sources, 10, new ObjectMapper())
                .replay(records);

        for (DeepBookSourceDefinition source : sources) {
            LocalBookSnapshot expected = live.get(source.id());
            LocalBookSnapshot actual = replay.book(source.id()).orElseThrow();
            assertEquals(expected.sequence(), actual.sequence(), source.id() + " sequence");
            assertEquals(expected.quality(), actual.quality(), source.id() + " quality");
            assertEquals(expected.bestBid(), actual.bestBid(), source.id() + " best bid");
            assertEquals(expected.bestAsk(), actual.bestAsk(), source.id() + " best ask");
            assertEquals(expected.bids(), actual.bids(), source.id() + " bids");
            assertEquals(expected.asks(), actual.asks(), source.id() + " asks");
        }
    }

    @Test
    void replayIgnoresMessagesAfterRecoveryUntilNextGeneration() {
        DeepBookSourceDefinition source = DeepBookSourceCatalog.okx("BTC-USDT");
        List<RawEnvelope> records = List.of(
                envelope(RawRecordType.CONNECT, source, 1L, "", "CONNECTED"),
                envelope(
                        RawRecordType.WS_MESSAGE,
                        source,
                        1L,
                        "{\"arg\":{\"channel\":\"books\",\"instId\":\"BTC-USDT\"},"
                                + "\"action\":\"snapshot\",\"data\":[{"
                                + "\"bids\":[[\"100.0\",\"1.0\",\"0\",\"1\"]],"
                                + "\"asks\":[[\"101.0\",\"2.0\",\"0\",\"1\"]],"
                                + "\"ts\":\"1000\",\"seqId\":10,\"prevSeqId\":-1}]}",
                        ""
                ),
                envelope(RawRecordType.RECOVERY, source, 1L, "", "gap"),
                envelope(
                        RawRecordType.WS_MESSAGE,
                        source,
                        1L,
                        "{\"arg\":{\"channel\":\"books\",\"instId\":\"BTC-USDT\"},"
                                + "\"action\":\"update\",\"data\":[{"
                                + "\"bids\":[[\"100.0\",\"9.0\",\"0\",\"1\"]],"
                                + "\"asks\":[],\"ts\":\"2000\",\"seqId\":11,"
                                + "\"prevSeqId\":10}]}",
                        ""
                )
        );

        LocalBookSnapshot replayed = new RawReplayProcessor(
                List.of(source), 10, new ObjectMapper())
                .replay(records)
                .book(source.id())
                .orElseThrow();

        assertEquals(10L, replayed.sequence());
        assertEquals("1.0", replayed.bestBid().quantity().toPlainString());
    }
    @Test
    void replayRejectsExplicitUnsafeMarker() {
        DeepBookSourceDefinition source = DeepBookSourceCatalog.okx("BTC-USDT");
        RawEnvelope unsafe = envelope(
                RawRecordType.RECOVERY,
                source,
                1L,
                "",
                "REPLAY_UNSAFE recorder dropped 1 records"
        );
        assertThrows(
                IllegalStateException.class,
                () -> new RawReplayProcessor(List.of(source), 10, new ObjectMapper())
                        .replay(List.of(unsafe))
        );
    }

    private static Map<String, LocalBookSnapshot> processLikeLive(
            List<DeepBookSourceDefinition> sources,
            List<RawEnvelope> records
    ) {
        Map<String, LocalOrderBookBuilder> builders = new LinkedHashMap<>();
        Map<String, List<RawEnvelope>> pendingBinance = new LinkedHashMap<>();
        for (DeepBookSourceDefinition source : sources) {
            builders.put(source.id(), LocalOrderBookBuilderFactory.create(source));
            pendingBinance.put(source.id(), new ArrayList<>());
        }
        for (RawEnvelope record : records) {
            LocalOrderBookBuilder builder = builders.get(record.sourceId());
            if (record.recordType() == RawRecordType.WS_MESSAGE
                    && "BINANCE_US".equals(record.exchange())
                    && builder.quality() == com.example.hft.datasource.book.BookQuality.EMPTY) {
                pendingBinance.get(record.sourceId()).add(record);
            } else if (record.recordType() == RawRecordType.REST_SNAPSHOT
                    && record.detail().startsWith("BEFORE_APPLY")) {
                builder.loadSnapshot(record.payload(), record.receivedEpochMillis());
                for (RawEnvelope pending : pendingBinance.get(record.sourceId())) {
                    builder.onMessage(pending.payload(), pending.receivedEpochMillis());
                }
                pendingBinance.get(record.sourceId()).clear();
            } else if (record.recordType() == RawRecordType.WS_MESSAGE) {
                builder.onMessage(record.payload(), record.receivedEpochMillis());
            }
        }
        Map<String, LocalBookSnapshot> result = new LinkedHashMap<>();
        builders.forEach((sourceId, builder) -> result.put(sourceId, builder.snapshot(10)));
        return result;
    }

    private static List<RawEnvelope> records(List<DeepBookSourceDefinition> sources) {
        DeepBookSourceDefinition binance = sources.get(0);
        DeepBookSourceDefinition okx = sources.get(1);
        DeepBookSourceDefinition kraken = sources.get(2);
        long checksum = KrakenBookChecksum.calculate(
                List.of(new ChecksumLevel("101.0", "2.500")),
                List.of(new ChecksumLevel("100.0", "1.250"))
        );
        List<RawEnvelope> records = new ArrayList<>();
        for (DeepBookSourceDefinition source : sources) {
            records.add(envelope(RawRecordType.CONNECT, source, 1L, "", "CONNECTED"));
        }
        records.add(envelope(
                RawRecordType.WS_MESSAGE,
                binance,
                1L,
                "{\"e\":\"depthUpdate\",\"E\":1000,\"s\":\"BTCUSDT\",\"U\":100,\"u\":101,"
                        + "\"b\":[[\"100.0\",\"1.1\"]],\"a\":[]}",
                ""
        ));
        String binanceSnapshot = "{\"lastUpdateId\":100,"
                + "\"bids\":[[\"100.0\",\"1.0\"],[\"99.0\",\"2.0\"]],"
                + "\"asks\":[[\"101.0\",\"1.5\"],[\"102.0\",\"2.5\"]]}";
        records.add(envelope(
                RawRecordType.REST_SNAPSHOT,
                binance,
                1L,
                binanceSnapshot,
                "BEFORE_APPLY"
        ));
        records.add(envelope(
                RawRecordType.REST_SNAPSHOT,
                binance,
                1L,
                binanceSnapshot,
                "AFTER_APPLY status=SNAPSHOT_LOADED"
        ));
        records.add(envelope(
                RawRecordType.WS_MESSAGE,
                binance,
                1L,
                "{\"e\":\"depthUpdate\",\"E\":2000,\"s\":\"BTCUSDT\",\"U\":102,\"u\":102,"
                        + "\"b\":[],\"a\":[[\"101.0\",\"1.6\"]]}",
                ""
        ));
        records.add(envelope(
                RawRecordType.WS_MESSAGE,
                okx,
                1L,
                "{\"arg\":{\"channel\":\"books\",\"instId\":\"BTC-USDT\"},"
                        + "\"action\":\"snapshot\",\"data\":[{\"bids\":[[\"100.0\",\"1.0\",\"0\",\"1\"]],"
                        + "\"asks\":[[\"101.0\",\"2.0\",\"0\",\"1\"]],"
                        + "\"ts\":\"1000\",\"seqId\":10,\"prevSeqId\":-1}]}",
                ""
        ));
        records.add(envelope(
                RawRecordType.WS_MESSAGE,
                okx,
                1L,
                "{\"arg\":{\"channel\":\"books\",\"instId\":\"BTC-USDT\"},"
                        + "\"action\":\"update\",\"data\":[{\"bids\":[[\"100.0\",\"1.1\",\"0\",\"1\"]],"
                        + "\"asks\":[],\"ts\":\"2000\",\"seqId\":11,\"prevSeqId\":10}]}",
                ""
        ));
        records.add(envelope(
                RawRecordType.WS_MESSAGE,
                kraken,
                1L,
                "{\"channel\":\"book\",\"type\":\"snapshot\",\"data\":[{"
                        + "\"symbol\":\"BTC/USD\","
                        + "\"bids\":[{\"price\":\"100.0\",\"qty\":\"1.250\"}],"
                        + "\"asks\":[{\"price\":\"101.0\",\"qty\":\"2.500\"}],"
                        + "\"checksum\":" + checksum + ","
                        + "\"timestamp\":\"1970-01-01T00:00:01Z\"}]}",
                ""
        ));
        records.add(envelope(
                RawRecordType.WS_MESSAGE,
                kraken,
                1L,
                "{\"channel\":\"book\",\"type\":\"update\",\"data\":[{"
                        + "\"symbol\":\"BTC/USD\","
                        + "\"bids\":[{\"price\":\"100.0\",\"qty\":\"1.250\"}],"
                        + "\"asks\":[],\"checksum\":" + checksum + ","
                        + "\"timestamp\":\"1970-01-01T00:00:02Z\"}]}",
                ""
        ));
        return records;
    }

    private static RawEnvelope envelope(
            RawRecordType type,
            DeepBookSourceDefinition source,
            long generation,
            String payload,
            String detail
    ) {
        return new RawEnvelope(
                DataSourceModuleVersion.VERSION,
                type,
                generation,
                source.id(),
                source.exchange(),
                source.symbol(),
                RECEIVED,
                RECEIVED,
                payload,
                detail
        );
    }
}

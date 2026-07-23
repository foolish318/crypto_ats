package com.example.hft.datasource.deepbook.runtime;

import com.example.hft.datasource.book.BookQuality;
import com.example.hft.datasource.deepbook.DeepBookSourceCatalog;
import com.example.hft.datasource.deepbook.quality.KrakenBookChecksum;
import com.example.hft.datasource.deepbook.quality.KrakenBookChecksum.ChecksumLevel;
import java.time.Duration;
import java.util.List;


public final class StreamingLocalOrderBookSelfTest {
    private static final long RECEIVED_MILLIS = 3_000L;
    private static final Duration MAX_AGE = Duration.ofSeconds(10);

    private StreamingLocalOrderBookSelfTest() {
    }

    public static void runAll() {
        testBinanceStreamingBook();
        testOkxStreamingBook();
        testKrakenStreamingBook();
        testMalformedUpdateRequestsRecovery();
    }

    private static void testBinanceStreamingBook() {
        LocalOrderBookBuilder builder = LocalOrderBookBuilderFactory.create(
                DeepBookSourceCatalog.binanceUs("BTCUSDT"), MAX_AGE);
        BookUpdateResult snapshot = builder.loadSnapshot(
                """
                {"lastUpdateId":100,
                 "bids":[["100.0","1.0"],["99.0","2.0"]],
                 "asks":[["101.0","1.5"],["102.0","2.5"]]}
                """,
                RECEIVED_MILLIS
        );
        assertStatus(BookUpdateStatus.SNAPSHOT_LOADED, snapshot, "Binance snapshot");
        assertEquals(BookQuality.BOOTSTRAPPING, builder.quality(), "Binance bootstrap quality");

        BookUpdateResult first = builder.onMessage(
                """
                {"e":"depthUpdate","E":1000,"s":"BTCUSDT","U":100,"u":101,
                 "b":[["100.0","1.1"]],"a":[]}
                """,
                RECEIVED_MILLIS
        );
        assertStatus(BookUpdateStatus.APPLIED, first, "Binance bridge");
        assertEquals(BookQuality.LIVE, builder.quality(), "Binance live quality");

        BookUpdateResult second = builder.onMessage(
                """
                {"e":"depthUpdate","E":2000,"s":"BTCUSDT","U":102,"u":102,
                 "b":[],"a":[["101.0","1.6"]]}
                """,
                RECEIVED_MILLIS
        );
        assertStatus(BookUpdateStatus.APPLIED, second, "Binance contiguous update");

        BookUpdateResult gap = builder.onMessage(
                """
                {"e":"depthUpdate","E":2500,"s":"BTCUSDT","U":104,"u":104,
                 "b":[],"a":[]}
                """,
                RECEIVED_MILLIS
        );
        assertStatus(BookUpdateStatus.GAP, gap, "Binance gap");
        assertTrue(gap.requiresRecovery(), "Binance gap must request recovery");
    }

    private static void testOkxStreamingBook() {
        LocalOrderBookBuilder builder = LocalOrderBookBuilderFactory.create(
                DeepBookSourceCatalog.okx("BTC-USDT"), MAX_AGE);
        BookUpdateResult snapshot = builder.onMessage(
                """
                {"arg":{"channel":"books","instId":"BTC-USDT"},"action":"snapshot",
                 "data":[{"bids":[["100.0","1.0","0","1"]],"asks":[["101.0","2.0","0","1"]],
                 "ts":"1000","seqId":10,"prevSeqId":-1}]}
                """,
                RECEIVED_MILLIS
        );
        assertStatus(BookUpdateStatus.SNAPSHOT_LOADED, snapshot, "OKX snapshot");

        BookUpdateResult update = builder.onMessage(
                """
                {"arg":{"channel":"books","instId":"BTC-USDT"},"action":"update",
                 "data":[{"bids":[["100.0","1.1","0","1"]],"asks":[],
                 "ts":"2000","seqId":11,"prevSeqId":10}]}
                """,
                RECEIVED_MILLIS
        );
        assertStatus(BookUpdateStatus.APPLIED, update, "OKX update");

        BookUpdateResult gap = builder.onMessage(
                """
                {"arg":{"channel":"books","instId":"BTC-USDT"},"action":"update",
                 "data":[{"bids":[],"asks":[],
                 "ts":"2500","seqId":12,"prevSeqId":9}]}
                """,
                RECEIVED_MILLIS
        );
        assertStatus(BookUpdateStatus.GAP, gap, "OKX gap");
        assertTrue(gap.requiresRecovery(), "OKX gap must request recovery");
    }

    private static void testKrakenStreamingBook() {
        LocalOrderBookBuilder builder = LocalOrderBookBuilderFactory.create(
                DeepBookSourceCatalog.kraken("BTC/USD"), MAX_AGE);
        long checksum = KrakenBookChecksum.calculate(
                List.of(new ChecksumLevel("101.0", "2.500")),
                List.of(new ChecksumLevel("100.0", "1.250"))
        );
        BookUpdateResult snapshot = builder.onMessage(
                """
                {"channel":"book","type":"snapshot","data":[{
                 "symbol":"BTC/USD",
                 "bids":[{"price":"100.0","qty":"1.250"}],
                 "asks":[{"price":"101.0","qty":"2.500"}],
                 "checksum":%d,"timestamp":"1970-01-01T00:00:01Z"}]}
                """.formatted(checksum),
                RECEIVED_MILLIS
        );
        assertStatus(BookUpdateStatus.SNAPSHOT_LOADED, snapshot, "Kraken snapshot");

        BookUpdateResult update = builder.onMessage(
                """
                {"channel":"book","type":"update","data":[{
                 "symbol":"BTC/USD",
                 "bids":[{"price":"100.0","qty":"1.250"}],"asks":[],
                 "checksum":%d,"timestamp":"1970-01-01T00:00:02Z"}]}
                """.formatted(checksum),
                RECEIVED_MILLIS
        );
        assertStatus(BookUpdateStatus.APPLIED, update, "Kraken update");
        LocalBookSnapshot view = builder.snapshot(1);
        assertEquals("100.0", view.bestBid().price().toPlainString(), "Kraken best bid");
        assertEquals("101.0", view.bestAsk().price().toPlainString(), "Kraken best ask");

        BookUpdateResult badChecksum = builder.onMessage(
                """
                {"channel":"book","type":"update","data":[{
                 "symbol":"BTC/USD","bids":[],"asks":[],
                 "checksum":%d,"timestamp":"1970-01-01T00:00:02.500Z"}]}
                """.formatted(checksum + 1),
                RECEIVED_MILLIS
        );
        assertStatus(BookUpdateStatus.CHECKSUM_FAILED, badChecksum, "Kraken checksum");
        assertTrue(badChecksum.requiresRecovery(), "Kraken checksum must request recovery");
    }


    private static void testMalformedUpdateRequestsRecovery() {
        LocalOrderBookBuilder builder = LocalOrderBookBuilderFactory.create(
                DeepBookSourceCatalog.okx("BTC-USDT"), MAX_AGE);
        BookUpdateResult malformed = builder.onMessage(
                "{\"arg\":{\"channel\":\"books\",\"instId\":\"BTC-USDT\"},"
                        + "\"action\":\"snapshot\",\"data\":[]}",
                RECEIVED_MILLIS
        );
        assertStatus(BookUpdateStatus.PARSE_FAILED, malformed, "malformed update");
        assertEquals(BookQuality.DEGRADED, builder.quality(), "malformed update quality");
        assertTrue(malformed.requiresRecovery(), "malformed update must request recovery");
    }
    private static void assertStatus(
            BookUpdateStatus expected,
            BookUpdateResult actual,
            String label
    ) {
        assertEquals(expected, actual.status(), label);
    }

    private static void assertTrue(boolean value, String label) {
        if (!value) {
            throw new AssertionError(label);
        }
    }

    private static void assertEquals(Object expected, Object actual, String label) {
        if (!expected.equals(actual)) {
            throw new AssertionError(label + ": expected=" + expected + " actual=" + actual);
        }
    }
}

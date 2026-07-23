package com.example.hft.datasource.deepbook.quality;

import com.example.hft.datasource.deepbook.DeepBookSourceCatalog;
import com.example.hft.datasource.deepbook.quality.KrakenBookChecksum.ChecksumLevel;
import java.time.Duration;
import java.time.Instant;
import java.util.List;


public final class DeepBookQualityValidatorSelfTest {
    private static final Instant VALIDATION_TIME = Instant.ofEpochMilli(3_000);

    private DeepBookQualityValidatorSelfTest() {
    }

    public static void runAll() {
        testBinanceSequenceGate();
        testOkxSequenceGate();
        testKrakenChecksumGate();
        testKrakenOfficialChecksumExample();
    }

    private static void testBinanceSequenceGate() {
        DeepBookQualityValidator validator = new DeepBookQualityValidator(Duration.ofSeconds(10));
        String snapshot = """
                {"lastUpdateId":100,
                 "bids":[["100.0","1.0"],["99.0","2.0"]],
                 "asks":[["101.0","1.5"],["102.0","2.5"]]}
                """;
        String first = """
                {"e":"depthUpdate","E":1000,"s":"BTCUSDT","U":101,"u":101,
                 "b":[["100.0","1.1"]],"a":[]}
                """;
        String second = """
                {"e":"depthUpdate","E":2000,"s":"BTCUSDT","U":102,"u":102,
                 "b":[],"a":[["101.0","1.6"]]}
                """;
        DeepBookQualityReport accepted = validator.validate(
                DeepBookSourceCatalog.binanceUs("BTCUSDT"),
                snapshot,
                List.of(first, second),
                VALIDATION_TIME
        );
        assertAccepted(accepted, "contiguous Binance updates");

        String gap = second.replace("\"U\":102,\"u\":102", "\"U\":103,\"u\":103");
        DeepBookQualityReport rejected = validator.validate(
                DeepBookSourceCatalog.binanceUs("BTCUSDT"),
                snapshot,
                List.of(first, gap),
                VALIDATION_TIME
        );
        assertRejected(rejected, "Binance sequence gap");
    }

    private static void testOkxSequenceGate() {
        DeepBookQualityValidator validator = new DeepBookQualityValidator(Duration.ofSeconds(10));
        String snapshot = """
                {"arg":{"channel":"books","instId":"BTC-USDT"},"action":"snapshot",
                 "data":[{"bids":[["100.0","1.0","0","1"]],"asks":[["101.0","2.0","0","1"]],
                 "ts":"1000","seqId":10,"prevSeqId":-1}]}
                """;
        String update = """
                {"arg":{"channel":"books","instId":"BTC-USDT"},"action":"update",
                 "data":[{"bids":[["100.0","1.1","0","1"]],"asks":[],
                 "ts":"2000","seqId":11,"prevSeqId":10}]}
                """;
        DeepBookQualityReport accepted = validator.validate(
                DeepBookSourceCatalog.okx("BTC-USDT"),
                null,
                List.of(snapshot, update),
                VALIDATION_TIME
        );
        assertAccepted(accepted, "contiguous OKX updates");

        String gap = update.replace("\"prevSeqId\":10", "\"prevSeqId\":9");
        DeepBookQualityReport rejected = validator.validate(
                DeepBookSourceCatalog.okx("BTC-USDT"),
                null,
                List.of(snapshot, gap),
                VALIDATION_TIME
        );
        assertRejected(rejected, "OKX prevSeqId gap");
    }

    private static void testKrakenChecksumGate() {
        DeepBookQualityValidator validator = new DeepBookQualityValidator(Duration.ofSeconds(10));
        List<ChecksumLevel> asks = List.of(new ChecksumLevel("101.0", "2.500"));
        List<ChecksumLevel> bids = List.of(new ChecksumLevel("100.0", "1.250"));
        long checksum = KrakenBookChecksum.calculate(asks, bids);
        String snapshot = """
                {"channel":"book","type":"snapshot","data":[{
                 "symbol":"BTC/USD",
                 "bids":[{"price":"100.0","qty":"1.250"}],
                 "asks":[{"price":"101.0","qty":"2.500"}],
                 "checksum":%d,"timestamp":"1970-01-01T00:00:01Z"}]}
                """.formatted(checksum);
        String update = """
                {"channel":"book","type":"update","data":[{
                 "symbol":"BTC/USD",
                 "bids":[{"price":"100.0","qty":"1.250"}],"asks":[],
                 "checksum":%d,"timestamp":"1970-01-01T00:00:02Z"}]}
                """.formatted(checksum);
        DeepBookQualityReport accepted = validator.validate(
                DeepBookSourceCatalog.kraken("BTC/USD"),
                null,
                List.of(snapshot, update),
                VALIDATION_TIME
        );
        assertAccepted(accepted, "valid Kraken CRC32");

        String badChecksum = update.replace("\"checksum\":" + checksum, "\"checksum\":" + (checksum + 1));
        DeepBookQualityReport rejected = validator.validate(
                DeepBookSourceCatalog.kraken("BTC/USD"),
                null,
                List.of(snapshot, badChecksum),
                VALIDATION_TIME
        );
        assertRejected(rejected, "Kraken CRC32 mismatch");
    }

    private static void testKrakenOfficialChecksumExample() {
        List<ChecksumLevel> asks = List.of(
                new ChecksumLevel("45285.2", "0.00100000"),
                new ChecksumLevel("45286.4", "1.54571953"),
                new ChecksumLevel("45286.6", "1.54571109"),
                new ChecksumLevel("45289.6", "1.54560911"),
                new ChecksumLevel("45290.2", "0.15890660"),
                new ChecksumLevel("45291.8", "1.54553491"),
                new ChecksumLevel("45294.7", "0.04454749"),
                new ChecksumLevel("45296.1", "0.35380000"),
                new ChecksumLevel("45297.5", "0.09945542"),
                new ChecksumLevel("45299.5", "0.18772827")
        );
        List<ChecksumLevel> bids = List.of(
                new ChecksumLevel("45283.5", "0.10000000"),
                new ChecksumLevel("45283.4", "1.54582015"),
                new ChecksumLevel("45282.1", "0.10000000"),
                new ChecksumLevel("45281.0", "0.10000000"),
                new ChecksumLevel("45280.3", "1.54592586"),
                new ChecksumLevel("45279.0", "0.07990000"),
                new ChecksumLevel("45277.6", "0.03310103"),
                new ChecksumLevel("45277.5", "0.30000000"),
                new ChecksumLevel("45277.3", "1.54602737"),
                new ChecksumLevel("45276.6", "0.15445238")
        );
        long actual = KrakenBookChecksum.calculate(asks, bids);
        if (actual != 3_310_070_434L) {
            throw new AssertionError("Kraken official CRC32 example: expected=3310070434 actual=" + actual);
        }
    }

    private static void assertAccepted(DeepBookQualityReport report, String label) {
        if (!report.accepted()) {
            throw new AssertionError(label + ": expected accepted, failures=" + report.failureSummary());
        }
    }

    private static void assertRejected(DeepBookQualityReport report, String label) {
        if (report.accepted()) {
            throw new AssertionError(label + ": expected rejection");
        }
    }
}

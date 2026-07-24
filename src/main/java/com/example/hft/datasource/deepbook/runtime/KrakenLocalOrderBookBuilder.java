package com.example.hft.datasource.deepbook.runtime;

import com.example.hft.datasource.book.BookQuality;
import com.example.hft.datasource.deepbook.DeepBookSourceDefinition;
import com.example.hft.datasource.instrument.Instrument;
import com.example.hft.datasource.deepbook.quality.KrakenBookChecksum;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.time.Instant;


public final class KrakenLocalOrderBookBuilder extends AbstractLocalOrderBookBuilder {
    public KrakenLocalOrderBookBuilder(DeepBookSourceDefinition source, Duration maxEventAge) {
        this(source, maxEventAge, null);
    }

    public KrakenLocalOrderBookBuilder(
            DeepBookSourceDefinition source,
            Duration maxEventAge,
            Instrument instrument
    ) {
        super(source, maxEventAge, instrument);
        if (!"KRAKEN".equals(source.exchange())) {
            throw new IllegalArgumentException("Kraken builder requires a KRAKEN source");
        }
    }

    @Override
    public BookUpdateResult loadSnapshot(String payload, long receivedEpochMillis) {
        return onMessage(payload, receivedEpochMillis);
    }

    @Override
    public BookUpdateResult onMessage(String payload, long receivedEpochMillis) {
        long parseStart = System.nanoTime();
        try {
            ParsedPayload parsed = parse(payload);
            JsonNode root = parsed.root();
            if (!source.channel().equals(root.path("channel").asText(""))) {
                return ignored(parsed.parseNanos(), "not the configured Kraken book channel");
            }
            JsonNode data = requiredFirst(root.path("data"));
            if (!source.symbol().equals(data.path("symbol").asText(""))) {
                return ignored(parsed.parseNanos(), "different Kraken symbol");
            }
            String type = root.path("type").asText("");
            Instant eventTime = Instant.parse(data.path("timestamp").asText(""));
            requireFresh(eventTime, receivedEpochMillis);
            if (quality == BookQuality.LIVE && eventTime.isBefore(exchangeTime)) {
                return rejected(
                        BookUpdateStatus.GAP,
                        BookQuality.GAP_DETECTED,
                        parsed.parseNanos(),
                        System.nanoTime(),
                        "Kraken timestamp moved backwards"
                );
            }
            long bookStart = System.nanoTime();

            BookUpdateStatus status;
            if ("snapshot".equals(type)) {
                book.loadObjectSnapshot(data.path("bids"), data.path("asks"));
                status = BookUpdateStatus.SNAPSHOT_LOADED;
            } else if ("update".equals(type)) {
                if (quality != BookQuality.LIVE) {
                    return ignored(parsed.parseNanos(), "waiting for Kraken snapshot");
                }
                book.applyObjectUpdates(data.path("bids"), data.path("asks"));
                status = BookUpdateStatus.APPLIED;
            } else {
                return ignored(parsed.parseNanos(), "Kraken control message");
            }

            book.truncate(source.depthLevels());
            if (book.isCrossed()) {
                return rejected(
                        BookUpdateStatus.CROSSED,
                        BookQuality.CROSSED,
                        parsed.parseNanos(),
                        bookStart,
                        "best bid is not below best ask"
                );
            }
            long expectedChecksum = nonNegativeLong(data, "checksum");
            long actualChecksum = KrakenBookChecksum.calculate(book.checksumAsks(), book.checksumBids());
            if (actualChecksum != expectedChecksum) {
                return rejected(
                        BookUpdateStatus.CHECKSUM_FAILED,
                        BookQuality.CHECKSUM_FAILED,
                        parsed.parseNanos(),
                        bookStart,
                        "expected=" + expectedChecksum + " actual=" + actualChecksum
                );
            }
            return accepted(
                    status,
                    eventTime.toEpochMilli(),
                    eventTime,
                    receivedEpochMillis,
                    parsed.parseNanos(),
                    bookStart,
                    "Kraken " + type + " applied; crc32=" + actualChecksum
            );
        } catch (Exception e) {
            return parseFailure(parseStart, e);
        }
    }

    @Override
    public void reset() {
        book.clear();
        quality = BookQuality.EMPTY;
        sequence = -1L;
        exchangeTime = Instant.EPOCH;
        lastReceiveTime = Instant.EPOCH;
        lastAppliedTime = Instant.EPOCH;
    }

    private static JsonNode requiredFirst(JsonNode array) {
        if (!array.isArray() || array.isEmpty()) {
            throw new IllegalArgumentException("Kraken data must contain a book object");
        }
        return array.get(0);
    }

    private static long nonNegativeLong(JsonNode root, String field) {
        long value = root.path(field).asLong(Long.MIN_VALUE);
        if (value < 0) {
            throw new IllegalArgumentException(field + " must be non-negative");
        }
        return value;
    }
}

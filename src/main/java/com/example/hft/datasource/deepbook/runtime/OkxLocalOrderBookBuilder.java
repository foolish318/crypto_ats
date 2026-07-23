package com.example.hft.datasource.deepbook.runtime;

import com.example.hft.datasource.book.BookQuality;
import com.example.hft.datasource.deepbook.DeepBookSourceDefinition;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.time.Instant;


public final class OkxLocalOrderBookBuilder extends AbstractLocalOrderBookBuilder {
    public OkxLocalOrderBookBuilder(DeepBookSourceDefinition source, Duration maxEventAge) {
        super(source, maxEventAge);
        if (!"OKX".equals(source.exchange())) {
            throw new IllegalArgumentException("OKX builder requires an OKX source");
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
            if (!source.channel().equals(root.path("arg").path("channel").asText(""))
                    || !source.symbol().equals(root.path("arg").path("instId").asText(""))) {
                return ignored(parsed.parseNanos(), "not the configured OKX book channel");
            }
            String action = root.path("action").asText("");
            if (!"snapshot".equals(action) && !"update".equals(action)) {
                return ignored(parsed.parseNanos(), "OKX control message");
            }
            JsonNode data = requiredFirst(root.path("data"));
            long eventSequence = nonNegativeLong(data, "seqId");
            long previousSequence = data.path("prevSeqId").asLong(Long.MIN_VALUE);
            Instant eventTime = Instant.ofEpochMilli(positiveLong(data, "ts"));
            requireFresh(eventTime, receivedEpochMillis);
            long bookStart = System.nanoTime();

            BookUpdateStatus status;
            if ("snapshot".equals(action)) {
                book.loadArraySnapshot(data.path("bids"), data.path("asks"));
                status = BookUpdateStatus.SNAPSHOT_LOADED;
            } else if ("update".equals(action)) {
                if (quality != BookQuality.LIVE) {
                    return ignored(parsed.parseNanos(), "waiting for OKX snapshot");
                }
                if (previousSequence != sequence || eventSequence < previousSequence) {
                    return rejected(
                            BookUpdateStatus.GAP,
                            BookQuality.GAP_DETECTED,
                            parsed.parseNanos(),
                            bookStart,
                            "expected prevSeqId=" + sequence
                                    + " actual prevSeqId=" + previousSequence
                                    + " seqId=" + eventSequence
                    );
                }
                book.applyArrayUpdates(data.path("bids"), data.path("asks"));
                status = BookUpdateStatus.APPLIED;
            } else {
                return ignored(parsed.parseNanos(), "OKX control message");
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
            return accepted(
                    status,
                    eventSequence,
                    eventTime,
                    parsed.parseNanos(),
                    bookStart,
                    "OKX " + action + " applied"
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
    }

    private static JsonNode requiredFirst(JsonNode array) {
        if (!array.isArray() || array.isEmpty()) {
            throw new IllegalArgumentException("OKX data must contain a book object");
        }
        return array.get(0);
    }

    private static long positiveLong(JsonNode root, String field) {
        long value = root.path(field).asLong(Long.MIN_VALUE);
        if (value <= 0) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return value;
    }

    private static long nonNegativeLong(JsonNode root, String field) {
        long value = root.path(field).asLong(Long.MIN_VALUE);
        if (value < 0) {
            throw new IllegalArgumentException(field + " must be non-negative");
        }
        return value;
    }
}

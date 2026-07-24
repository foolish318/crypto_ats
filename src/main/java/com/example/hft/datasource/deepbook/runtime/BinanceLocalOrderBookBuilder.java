package com.example.hft.datasource.deepbook.runtime;

import com.example.hft.datasource.book.BookQuality;
import com.example.hft.datasource.deepbook.DeepBookSourceDefinition;
import com.example.hft.datasource.instrument.Instrument;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.time.Instant;


public final class BinanceLocalOrderBookBuilder extends AbstractLocalOrderBookBuilder {
    private boolean bridged;

    public BinanceLocalOrderBookBuilder(DeepBookSourceDefinition source, Duration maxEventAge) {
        this(source, maxEventAge, null);
    }

    public BinanceLocalOrderBookBuilder(
            DeepBookSourceDefinition source,
            Duration maxEventAge,
            Instrument instrument
    ) {
        super(source, maxEventAge, instrument);
        if (!"BINANCE_US".equals(source.exchange())) {
            throw new IllegalArgumentException("Binance builder requires a BINANCE_US source");
        }
    }

    @Override
    public BookUpdateResult loadSnapshot(String payload, long receivedEpochMillis) {
        long parseStart = System.nanoTime();
        try {
            ParsedPayload parsed = parse(payload);
            JsonNode root = parsed.root();
            long lastUpdateId = positiveLong(root, "lastUpdateId");
            long bookStart = System.nanoTime();
            book.loadArraySnapshot(root.path("bids"), root.path("asks"));
            if (book.isCrossed()) {
                return rejected(
                        BookUpdateStatus.CROSSED,
                        BookQuality.CROSSED,
                        parsed.parseNanos(),
                        bookStart,
                        "Binance snapshot is empty or crossed"
                );
            }
            sequence = lastUpdateId;
            exchangeTime = Instant.ofEpochMilli(receivedEpochMillis);
            lastReceiveTime = exchangeTime;
            lastAppliedTime = exchangeTime;
            bookVersion++;
            quality = BookQuality.BOOTSTRAPPING;
            bridged = false;
            acceptedMessages++;
            return new BookUpdateResult(
                    BookUpdateStatus.SNAPSHOT_LOADED,
                    quality,
                    sequence,
                    exchangeTime.toEpochMilli(),
                    parsed.parseNanos(),
                    System.nanoTime() - bookStart,
                    "REST snapshot loaded; waiting for U/u bridge"
            );
        } catch (Exception e) {
            return parseFailure(parseStart, e);
        }
    }

    @Override
    public BookUpdateResult onMessage(String payload, long receivedEpochMillis) {
        long parseStart = System.nanoTime();
        try {
            ParsedPayload parsed = parse(payload);
            JsonNode root = parsed.root().has("data") ? parsed.root().path("data") : parsed.root();
            if (!source.symbol().equals(root.path("s").asText(""))) {
                return ignored(parsed.parseNanos(), "different symbol");
            }

            long firstUpdateId = positiveLong(root, "U");
            long finalUpdateId = positiveLong(root, "u");
            Instant eventTime = Instant.ofEpochMilli(positiveLong(root, "E"));
            requireFresh(eventTime, receivedEpochMillis);
            if (firstUpdateId > finalUpdateId) {
                throw new IllegalArgumentException("Binance U must not exceed u");
            }
            if (quality == BookQuality.EMPTY) {
                return ignored(parsed.parseNanos(), "waiting for REST snapshot");
            }
            if (finalUpdateId <= sequence) {
                return new BookUpdateResult(
                        BookUpdateStatus.STALE,
                        quality,
                        sequence,
                        exchangeTime.toEpochMilli(),
                        parsed.parseNanos(),
                        0L,
                        "final update id is not newer than the local book"
                );
            }

            long expected = sequence + 1;
            boolean contiguous = bridged
                    ? firstUpdateId == expected
                    : firstUpdateId <= expected && finalUpdateId >= expected;
            if (!contiguous) {
                return rejected(
                        BookUpdateStatus.GAP,
                        BookQuality.GAP_DETECTED,
                        parsed.parseNanos(),
                        System.nanoTime(),
                        "expected=" + expected + " U=" + firstUpdateId + " u=" + finalUpdateId
                );
            }

            long bookStart = System.nanoTime();
            book.applyArrayUpdates(root.path("b"), root.path("a"));
            if (book.isCrossed()) {
                return rejected(
                        BookUpdateStatus.CROSSED,
                        BookQuality.CROSSED,
                        parsed.parseNanos(),
                        bookStart,
                        "best bid is not below best ask"
                );
            }
            bridged = true;
            return accepted(
                    BookUpdateStatus.APPLIED,
                    finalUpdateId,
                    eventTime,
                    receivedEpochMillis,
                    parsed.parseNanos(),
                    bookStart,
                    "contiguous Binance diff applied"
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
        bridged = false;
    }

    private static long positiveLong(JsonNode root, String field) {
        long value = root.path(field).asLong(Long.MIN_VALUE);
        if (value <= 0) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return value;
    }
}

package com.example.hft.marketdata.model;

import java.util.List;

public record BookSnapshot(
        MarketEventHeader header,
        long bookVersion,
        BookHealth health,
        long lastUpdateEpochMillis,
        long lastAppliedMonotonicNanos,
        List<BookLevel> bids,
        List<BookLevel> asks
) {
    public BookSnapshot {
        if (header == null || health == null || bookVersion < 0L
                || lastUpdateEpochMillis < 0L) {
            throw new IllegalArgumentException("valid book snapshot metadata is required");
        }
        bids = List.copyOf(bids);
        asks = List.copyOf(asks);
    }
}
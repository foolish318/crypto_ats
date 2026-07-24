package com.example.hft.marketdata.model;

public record MarketEventHeader(
        Venue venue,
        InstrumentId instrumentId,
        String venueSymbol,
        Long sourceSequence,
        long localSequence,
        long streamEpoch,
        long exchangeEpochMillis,
        long receiveEpochMillis,
        long receiveMonotonicNanos,
        long publishMonotonicNanos,
        int schemaVersion
) {
    public MarketEventHeader {
        if (venue == null || instrumentId == null
                || venueSymbol == null || venueSymbol.isBlank()) {
            throw new IllegalArgumentException("market event identity is required");
        }
        if (localSequence < 0L || streamEpoch < 0L
                || exchangeEpochMillis < 0L || receiveEpochMillis < 0L
                || schemaVersion <= 0) {
            throw new IllegalArgumentException("market event sequences, clocks, and schema must be valid");
        }
    }
}
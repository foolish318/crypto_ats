package com.example.hft.marketdata.model;

public record BookStatusChange(
        Venue venue,
        InstrumentId instrumentId,
        String venueSymbol,
        long streamEpoch,
        BookHealth previousHealth,
        BookHealth health,
        String reason,
        long observedEpochMillis
) {
    public BookStatusChange {
        if (venue == null || instrumentId == null || venueSymbol == null
                || venueSymbol.isBlank() || health == null
                || streamEpoch < 0L || observedEpochMillis < 0L) {
            throw new IllegalArgumentException("valid book status identity and state are required");
        }
        reason = reason == null ? "" : reason;
    }
}
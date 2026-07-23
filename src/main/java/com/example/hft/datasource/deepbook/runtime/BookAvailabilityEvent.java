package com.example.hft.datasource.deepbook.runtime;

import java.time.Instant;


public record BookAvailabilityEvent(
        String sourceId,
        String exchange,
        String venueSymbol,
        String canonicalInstrumentId,
        long generation,
        BookAvailabilityState state,
        String reason,
        Instant observedAt
) {
    public BookAvailabilityEvent {
        if (sourceId == null || sourceId.isBlank()
                || exchange == null || exchange.isBlank()
                || venueSymbol == null || venueSymbol.isBlank()
                || canonicalInstrumentId == null || canonicalInstrumentId.isBlank()
                || state == null
                || observedAt == null) {
            throw new IllegalArgumentException("book availability identity and state are required");
        }
        reason = reason == null ? "" : reason;
    }

    public boolean live() {
        return state == BookAvailabilityState.LIVE;
    }
}

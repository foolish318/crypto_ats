package com.example.hft.datasource.deepbook.runtime;

import java.time.Instant;


public record VenueBookSnapshot(
        String sourceId,
        String exchange,
        String venueSymbol,
        String canonicalInstrumentId,
        BookAvailabilityState state,
        long generation,
        long sequence,
        Instant eventTime,
        Instant receiveTime,
        long ageMillis,
        DecimalBookLevel bestBid,
        DecimalBookLevel bestAsk,
        LocalBookSnapshot depthSnapshot
) {
    public VenueBookSnapshot {
        if (sourceId == null || exchange == null || venueSymbol == null
                || canonicalInstrumentId == null || state == null
                || eventTime == null || receiveTime == null || depthSnapshot == null) {
            throw new IllegalArgumentException("venue book snapshot fields are required");
        }
        ageMillis = Math.max(0L, ageMillis);
    }
}

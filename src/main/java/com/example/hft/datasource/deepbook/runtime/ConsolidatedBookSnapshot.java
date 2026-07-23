package com.example.hft.datasource.deepbook.runtime;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;


public record ConsolidatedBookSnapshot(
        String canonicalInstrumentId,
        Instant observedAt,
        List<VenueBookSnapshot> venues,
        DecimalBookLevel bestBid,
        String bestBidVenue,
        DecimalBookLevel bestAsk,
        String bestAskVenue,
        BigDecimal nbboSpread,
        boolean crossed,
        boolean locked,
        int validVenueCount,
        boolean coherent,
        Instant watermark,
        long maxVenueSkewMillis
) {
    public ConsolidatedBookSnapshot {
        if (canonicalInstrumentId == null || canonicalInstrumentId.isBlank()
                || observedAt == null || venues == null) {
            throw new IllegalArgumentException("consolidated snapshot identity is required");
        }
        venues = List.copyOf(venues);
        if (validVenueCount != venues.size()) {
            throw new IllegalArgumentException("valid venue count must match venue snapshots");
        }
        maxVenueSkewMillis = Math.max(0L, maxVenueSkewMillis);
    }
}

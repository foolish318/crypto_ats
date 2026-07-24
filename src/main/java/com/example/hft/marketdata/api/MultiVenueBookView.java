package com.example.hft.marketdata.api;

import com.example.hft.marketdata.model.InstrumentId;
import com.example.hft.marketdata.model.Venue;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public record MultiVenueBookView(
        InstrumentId instrument,
        long observedAtEpochMillis,
        Map<Venue, OrderBookView> books
) {
    public MultiVenueBookView {
        if (instrument == null || observedAtEpochMillis < 0L || books == null) {
            throw new IllegalArgumentException("multi-venue identity and books are required");
        }
        books = Map.copyOf(books);
    }

    public Optional<OrderBookView> book(Venue venue) {
        return Optional.ofNullable(books.get(venue));
    }

    public Set<Venue> venues() {
        return books.keySet();
    }
}
package com.example.hft.marketdata.api;

import com.example.hft.marketdata.model.BookHealth;
import com.example.hft.marketdata.model.InstrumentId;
import com.example.hft.marketdata.model.Venue;

public record BookUpdateNotification(
        Venue venue,
        InstrumentId instrument,
        long bookVersion,
        long localSequence,
        long exchangeTimestamp,
        long receiveTimestamp,
        long publishTimestamp,
        BookHealth health
) {
}
package com.example.hft.marketdata.api;

import com.example.hft.datasource.deepbook.runtime.AcceptedLocalBookEvent;
import com.example.hft.datasource.deepbook.runtime.BookAvailabilityEvent;
import com.example.hft.datasource.deepbook.runtime.BookAvailabilityState;
import com.example.hft.marketdata.model.BookHealth;
import com.example.hft.marketdata.model.BookLevel;
import com.example.hft.marketdata.model.BookSnapshot;
import com.example.hft.marketdata.model.BookStatusChange;
import com.example.hft.marketdata.model.InstrumentId;
import com.example.hft.marketdata.model.MarketEventHeader;
import com.example.hft.marketdata.model.Venue;

final class CanonicalMarketDataMapper {
    private static final int SCHEMA_VERSION = 1;

    private CanonicalMarketDataMapper() {
    }

    static BookSnapshot book(AcceptedLocalBookEvent event) {
        Long sourceSequence = "KRAKEN".equals(event.exchange()) ? null : event.sequence();
        MarketEventHeader header = new MarketEventHeader(
                Venue.fromExchange(event.exchange()),
                new InstrumentId(event.canonicalInstrumentId()),
                event.symbol(),
                sourceSequence,
                event.localSequence(),
                event.generation(),
                event.exchangeTimeMillis(),
                event.acceptedEpochMillis(),
                event.receivedNanos(),
                event.publishNanos(),
                SCHEMA_VERSION
        );
        return new BookSnapshot(
                header,
                event.book().bookVersion(),
                BookHealth.LIVE,
                event.book().lastAppliedTime().toEpochMilli(),
                event.publishNanos(),
                event.book().bids().stream()
                        .map(level -> new BookLevel(level.price(), level.quantity()))
                        .toList(),
                event.book().asks().stream()
                        .map(level -> new BookLevel(level.price(), level.quantity()))
                        .toList()
        );
    }

    static BookStatusChange status(BookAvailabilityEvent event, BookHealth previous) {
        return new BookStatusChange(
                Venue.fromExchange(event.exchange()),
                new InstrumentId(event.canonicalInstrumentId()),
                event.venueSymbol(),
                event.generation(),
                previous,
                health(event.state()),
                event.reason(),
                event.observedAt().toEpochMilli()
        );
    }

    static BookHealth health(BookAvailabilityState state) {
        return switch (state) {
            case LIVE -> BookHealth.LIVE;
            case STALE -> BookHealth.STALE;
            case RECOVERING -> BookHealth.RECOVERING;
            case DISCONNECTED, STOPPED -> BookHealth.DISCONNECTED;
            case INVALID -> BookHealth.INVALID;
            case GAP -> BookHealth.GAP;
            case CHECKSUM_FAILED -> BookHealth.CHECKSUM_FAILED;
        };
    }
}
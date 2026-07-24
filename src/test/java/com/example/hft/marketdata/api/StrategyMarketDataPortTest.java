package com.example.hft.marketdata.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.hft.datasource.book.BookQuality;
import com.example.hft.datasource.deepbook.runtime.AcceptedLocalBookEvent;
import com.example.hft.datasource.deepbook.runtime.BookAvailabilityEvent;
import com.example.hft.datasource.deepbook.runtime.BookAvailabilityState;
import com.example.hft.datasource.deepbook.runtime.DecimalBookLevel;
import com.example.hft.datasource.deepbook.runtime.LocalBookSnapshot;
import com.example.hft.datasource.engine.MarketDataCache;
import com.example.hft.datasource.engine.MarketDataEngine;
import com.example.hft.datasource.engine.MarketDataEventBus;
import com.example.hft.datasource.transport.TransportType;
import com.example.hft.marketdata.model.BookHealth;
import com.example.hft.marketdata.model.InstrumentId;
import com.example.hft.marketdata.model.Venue;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class StrategyMarketDataPortTest {
    @Test
    void updatesReadableStateBeforePublishingNotification() {
        MarketDataEventBus bus = new MarketDataEventBus();
        MarketDataEngine engine = new MarketDataEngine(new MarketDataCache(), bus);
        DefaultStrategyMarketDataPort port = new DefaultStrategyMarketDataPort();
        bus.subscribe(port);
        AtomicLong observedVersion = new AtomicLong();
        port.subscribe(new StrategyMarketDataListener() {
            @Override
            public void onBookUpdated(BookUpdateNotification notification) {
                OrderBookView readable = port.getBook(
                        notification.venue(), notification.instrument()).orElseThrow();
                observedVersion.set(readable.bookVersion());
            }
        });

        engine.publishAccepted(event("OKX", "BTC-USDT", 1L, 11L, 7L, "100", "101"));

        assertEquals(7L, observedVersion.get());
        assertEquals(7L, port.getBook(Venue.OKX, new InstrumentId("BTC-USDT"))
                .orElseThrow().bookVersion());
    }

    @Test
    void invalidationChangesHealthAndOldEpochCannotRestore() {
        MarketDataEventBus bus = new MarketDataEventBus();
        MarketDataEngine engine = new MarketDataEngine(new MarketDataCache(), bus);
        DefaultStrategyMarketDataPort port = new DefaultStrategyMarketDataPort();
        bus.subscribe(port);
        engine.publishAccepted(event("OKX", "BTC-USDT", 1L, 11L, 7L, "100", "101"));

        engine.onAvailability(status("OKX", "BTC-USDT", 1L, BookAvailabilityState.GAP));
        engine.publishAccepted(event("OKX", "BTC-USDT", 1L, 12L, 8L, "102", "103"));

        OrderBookView view = port.getBook(Venue.OKX, new InstrumentId("BTC-USDT"))
                .orElseThrow();
        assertEquals(BookHealth.GAP, view.health());
        assertEquals(7L, view.bookVersion());
    }

    @Test
    void multiVenueViewRetainsIndependentVersionsAgeAndImmutableDepth() {
        AtomicLong clock = new AtomicLong(2_000L);
        DefaultStrategyMarketDataPort port = new DefaultStrategyMarketDataPort(
                clock::get, com.example.hft.marketdata.recording.NormalizedEventSink.noop());
        port.onMarketData(event("OKX", "BTC-USDT", 1L, 11L, 7L, "100", "101"));
        port.onMarketData(event("BINANCE_US", "BTCUSDT", 2L, 21L, 9L, "99", "102"));

        MultiVenueBookView books = port.getBooks(new InstrumentId("BTC-USDT"));

        assertEquals(2, books.books().size());
        assertEquals(7L, books.book(Venue.OKX).orElseThrow().bookVersion());
        assertEquals(9L, books.book(Venue.BINANCE_US).orElseThrow().bookVersion());
        assertEquals(1_000L, books.book(Venue.OKX).orElseThrow().ageMillis());
        assertThrows(UnsupportedOperationException.class,
                () -> books.book(Venue.OKX).orElseThrow().topBids(1)
                        .add(new com.example.hft.marketdata.model.BookLevel(
                                BigDecimal.TEN, BigDecimal.ONE)));
    }

    private static AcceptedLocalBookEvent event(
            String exchange,
            String symbol,
            long epoch,
            long sequence,
            long version,
            String bid,
            String ask
    ) {
        LocalBookSnapshot snapshot = new LocalBookSnapshot(
                exchange + "-" + symbol,
                exchange,
                symbol,
                BookQuality.LIVE,
                sequence,
                version,
                Instant.ofEpochMilli(900L),
                Instant.ofEpochMilli(1_000L),
                Instant.ofEpochMilli(1_000L),
                List.of(new DecimalBookLevel(new BigDecimal(bid), BigDecimal.ONE)),
                List.of(new DecimalBookLevel(new BigDecimal(ask), BigDecimal.ONE))
        );
        return new AcceptedLocalBookEvent(
                exchange + "-" + symbol,
                exchange,
                symbol,
                "BTC-USDT",
                TransportType.WEBSOCKET,
                500L,
                900L,
                sequence,
                epoch,
                1_000L,
                version,
                600L,
                snapshot
        );
    }

    private static BookAvailabilityEvent status(
            String exchange,
            String symbol,
            long epoch,
            BookAvailabilityState state
    ) {
        return new BookAvailabilityEvent(
                exchange + "-" + symbol,
                exchange,
                symbol,
                "BTC-USDT",
                epoch,
                state,
                "test " + state,
                Instant.ofEpochMilli(1_100L)
        );
    }
}
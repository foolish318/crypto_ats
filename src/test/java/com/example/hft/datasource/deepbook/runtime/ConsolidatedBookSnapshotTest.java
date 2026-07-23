package com.example.hft.datasource.deepbook.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.hft.datasource.book.BookQuality;
import com.example.hft.datasource.transport.TransportType;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;


class ConsolidatedBookSnapshotTest {
    @Test
    void aggregatesDifferentVenueSymbolsByCanonicalInstrument() {
        AtomicLong clock = new AtomicLong(2_000L);
        CrossExchangeBookView view = new CrossExchangeBookView(
                Duration.ofSeconds(5),
                Duration.ofMillis(250),
                clock::get
        );
        view.onMarketData(event(
                "okx", "OKX", "BTC-USDT", 1L, 10L, 1_900L, "100", "102"));
        view.onMarketData(event(
                "kraken", "KRAKEN", "BTC/USD", 1L, 20L, 1_950L, "101", "103"));

        ConsolidatedBookSnapshot snapshot = view.consolidated("BTC-USD").orElseThrow();

        assertEquals(2, snapshot.validVenueCount());
        assertEquals(new BigDecimal("101"), snapshot.bestBid().price());
        assertEquals("KRAKEN|BTC/USD", snapshot.bestBidVenue());
        assertEquals(new BigDecimal("102"), snapshot.bestAsk().price());
        assertEquals("OKX|BTC-USDT", snapshot.bestAskVenue());
        assertEquals(new BigDecimal("1"), snapshot.nbboSpread());
        assertFalse(snapshot.crossed());
        assertFalse(snapshot.locked());
        assertTrue(snapshot.coherent());
        assertEquals(1_900L, snapshot.watermark().toEpochMilli());
    }

    @Test
    void staleVenueImmediatelyLeavesNbbo() {
        AtomicLong clock = new AtomicLong(2_000L);
        CrossExchangeBookView view = new CrossExchangeBookView(
                Duration.ofSeconds(5),
                Duration.ofMillis(250),
                clock::get
        );
        view.onMarketData(event(
                "okx", "OKX", "BTC-USDT", 1L, 10L, 1_900L, "100", "102"));
        view.onMarketData(event(
                "kraken", "KRAKEN", "BTC/USD", 1L, 20L, 1_950L, "101", "103"));

        view.onBookAvailability(new BookAvailabilityEvent(
                "kraken", "KRAKEN", "BTC/USD", "BTC-USD", 1L,
                BookAvailabilityState.STALE, "timeout", Instant.ofEpochMilli(2_000L)
        ));

        ConsolidatedBookSnapshot snapshot = view.consolidated("BTC-USD").orElseThrow();
        assertEquals(1, snapshot.validVenueCount());
        assertEquals("OKX|BTC-USDT", snapshot.bestBidVenue());
        assertEquals("OKX|BTC-USDT", snapshot.bestAskVenue());
    }

    @Test
    void freshnessAndCoherenceAreExplicit() {
        AtomicLong clock = new AtomicLong(5_000L);
        CrossExchangeBookView view = new CrossExchangeBookView(
                Duration.ofMillis(500),
                Duration.ofMillis(100),
                clock::get
        );
        view.onMarketData(event(
                "okx", "OKX", "BTC-USDT", 1L, 10L, 4_900L, "100", "102"));
        view.onMarketData(event(
                "kraken", "KRAKEN", "BTC/USD", 1L, 20L, 4_700L, "101", "103"));

        ConsolidatedBookSnapshot incoherent = view.consolidated("BTC-USD").orElseThrow();
        assertFalse(incoherent.coherent());
        assertEquals(200L, incoherent.maxVenueSkewMillis());

        clock.set(5_250L);
        ConsolidatedBookSnapshot freshOnly = view.consolidated("BTC-USD").orElseThrow();
        assertEquals(1, freshOnly.validVenueCount());
        assertTrue(freshOnly.coherent());
    }

    @Test
    void reportsLockedCrossedAndNormalMarkets() {
        assertSpreadState("100", "101", false, false);
        assertSpreadState("100", "100", false, true);
        assertSpreadState("101", "100", true, false);
    }

    private static void assertSpreadState(
            String bid,
            String ask,
            boolean crossed,
            boolean locked
    ) {
        CrossExchangeBookView view = new CrossExchangeBookView(
                Duration.ofSeconds(5),
                Duration.ofMillis(250),
                () -> 2_000L
        );
        view.onMarketData(event(
                "okx", "OKX", "BTC-USDT", 1L, 10L, 1_900L, bid, ask));
        ConsolidatedBookSnapshot snapshot = view.consolidated("BTC-USD").orElseThrow();
        assertEquals(crossed, snapshot.crossed());
        assertEquals(locked, snapshot.locked());
    }

    private static AcceptedLocalBookEvent event(
            String source,
            String exchange,
            String symbol,
            long generation,
            long sequence,
            long time,
            String bid,
            String ask
    ) {
        LocalBookSnapshot book = new LocalBookSnapshot(
                source,
                exchange,
                symbol,
                BookQuality.LIVE,
                sequence,
                Instant.ofEpochMilli(time),
                List.of(new DecimalBookLevel(new BigDecimal(bid), BigDecimal.ONE)),
                List.of(new DecimalBookLevel(new BigDecimal(ask), BigDecimal.ONE))
        );
        return new AcceptedLocalBookEvent(
                source,
                exchange,
                symbol,
                "BTC-USD",
                TransportType.WEBSOCKET,
                time,
                time,
                sequence,
                generation,
                time,
                book
        );
    }
}

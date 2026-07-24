package com.example.hft.marketdata.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.hft.marketdata.model.BookHealth;
import com.example.hft.marketdata.model.BookLevel;
import com.example.hft.marketdata.model.BookSnapshot;
import com.example.hft.marketdata.model.ExecutionSide;
import com.example.hft.marketdata.model.InstrumentId;
import com.example.hft.marketdata.model.MarketEventHeader;
import com.example.hft.marketdata.model.Venue;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class BookMathTest {
    @Test
    void computesDepthSweepAndVwapWithoutStrategyAssumptions() {
        OrderBookView view = new ImmutableOrderBookView(new BookSnapshot(
                new MarketEventHeader(
                        Venue.OKX, new InstrumentId("BTC-USDT"), "BTC-USDT",
                        10L, 1L, 1L, 1_000L, 1_001L, 10L, 20L, 1),
                1L, BookHealth.LIVE, 1_001L, 20L,
                List.of(new BookLevel(new BigDecimal("99"), new BigDecimal("2"))),
                List.of(
                        new BookLevel(new BigDecimal("100"), BigDecimal.ONE),
                        new BookLevel(new BigDecimal("101"), new BigDecimal("2")))
        ), () -> 1_001L);

        assertEquals(new BigDecimal("3"),
                BookMath.availableQuantity(view, ExecutionSide.BUY, 2));
        assertEquals(new BigDecimal("101"),
                BookMath.sweepPrice(view, ExecutionSide.BUY, new BigDecimal("2")).orElseThrow());
        assertEquals(0, new BigDecimal("100.5").compareTo(
                BookMath.executableVwap(view, ExecutionSide.BUY, new BigDecimal("2"))
                        .orElseThrow()));
        assertTrue(BookMath.executableVwap(
                view, ExecutionSide.BUY, new BigDecimal("4")).isEmpty());
    }
}
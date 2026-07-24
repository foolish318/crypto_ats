package com.example.hft.marketdata.recording;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.hft.datasource.book.BookQuality;
import com.example.hft.datasource.deepbook.runtime.AcceptedLocalBookEvent;
import com.example.hft.datasource.deepbook.runtime.BookAvailabilityEvent;
import com.example.hft.datasource.deepbook.runtime.BookAvailabilityState;
import com.example.hft.datasource.deepbook.runtime.DecimalBookLevel;
import com.example.hft.datasource.deepbook.runtime.LocalBookSnapshot;
import com.example.hft.datasource.transport.TransportType;
import com.example.hft.marketdata.api.DefaultStrategyMarketDataPort;
import com.example.hft.marketdata.api.OrderBookView;
import com.example.hft.marketdata.model.AggressorSide;
import com.example.hft.marketdata.model.BookHealth;
import com.example.hft.marketdata.model.InstrumentId;
import com.example.hft.marketdata.model.MarketEventHeader;
import com.example.hft.marketdata.model.PublicTrade;
import com.example.hft.marketdata.model.Venue;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NormalizedEventReplayTest {
    @TempDir
    Path tempDir;

    @Test
    void normalizedLogReplaysIdenticalBookTradeAndHealth() throws Exception {
        Path file = tempDir.resolve("normalized.jsonl");
        ObjectMapper mapper = new ObjectMapper();
        DefaultStrategyMarketDataPort live;
        try (AsyncNormalizedEventRecorder recorder =
                     new AsyncNormalizedEventRecorder(file, mapper, 64)) {
            live = new DefaultStrategyMarketDataPort(() -> 2_000L, recorder);
            live.onMarketData(bookEvent());
            live.onMarketData(trade());
            live.onBookAvailability(new BookAvailabilityEvent(
                    "okx-BTC-USDT", "OKX", "BTC-USDT", "BTC-USDT",
                    1L, BookAvailabilityState.STALE, "fixture stale",
                    Instant.ofEpochMilli(1_100L)));
            recorder.awaitDrained(5_000L);
        }

        DefaultStrategyMarketDataPort replayed = new DefaultStrategyMarketDataPort(
                () -> 2_000L, NormalizedEventSink.noop());
        NormalizedReplayResult result = new NormalizedEventReplay(mapper).replay(file, replayed);

        OrderBookView liveBook = live.getBook(Venue.OKX, new InstrumentId("BTC-USDT"))
                .orElseThrow();
        OrderBookView replayBook = replayed.getBook(Venue.OKX, new InstrumentId("BTC-USDT"))
                .orElseThrow();
        assertEquals(3L, result.records());
        assertEquals(liveBook.bookVersion(), replayBook.bookVersion());
        assertEquals(liveBook.health(), replayBook.health());
        assertEquals(BookHealth.STALE, replayBook.health());
        assertEquals(liveBook.topBids(10), replayBook.topBids(10));
        assertEquals(liveBook.topAsks(10), replayBook.topAsks(10));
        assertEquals(live.latestTrade(Venue.OKX, new InstrumentId("BTC-USDT")),
                replayed.latestTrade(Venue.OKX, new InstrumentId("BTC-USDT")));
    }

    private static AcceptedLocalBookEvent bookEvent() {
        LocalBookSnapshot snapshot = new LocalBookSnapshot(
                "okx-BTC-USDT", "OKX", "BTC-USDT", BookQuality.LIVE,
                10L, 5L,
                Instant.ofEpochMilli(900L), Instant.ofEpochMilli(1_000L),
                Instant.ofEpochMilli(1_000L),
                List.of(new DecimalBookLevel(new BigDecimal("100"), BigDecimal.ONE)),
                List.of(new DecimalBookLevel(new BigDecimal("101"), BigDecimal.ONE))
        );
        return new AcceptedLocalBookEvent(
                "okx-BTC-USDT", "OKX", "BTC-USDT", "BTC-USDT",
                TransportType.WEBSOCKET, 100L, 900L, 10L, 1L,
                1_000L, 5L, 200L, snapshot
        );
    }

    private static PublicTrade trade() {
        return new PublicTrade(
                "okx-trades-BTC-USDT",
                new MarketEventHeader(
                        Venue.OKX, new InstrumentId("BTC-USDT"), "BTC-USDT",
                        42L, 1L, 1L, 950L, 1_010L, 110L, 210L, 1),
                "42", new BigDecimal("100.5"), new BigDecimal("0.2"),
                AggressorSide.BUY
        );
    }
}
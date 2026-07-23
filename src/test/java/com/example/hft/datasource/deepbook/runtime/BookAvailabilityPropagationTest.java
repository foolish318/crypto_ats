package com.example.hft.datasource.deepbook.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.hft.datasource.book.BookQuality;
import com.example.hft.datasource.engine.MarketDataCache;
import com.example.hft.datasource.engine.MarketDataEngine;
import com.example.hft.datasource.engine.MarketDataEventBus;
import com.example.hft.datasource.transport.TransportType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;


class BookAvailabilityPropagationTest {
    @Test
    void staleInvalidatesCacheAndViewAndOldGenerationCannotRestore() {
        Fixture fixture = new Fixture();
        fixture.engine.onEvent(event(1L, 10L, "100", "101"));

        fixture.engine.onAvailability(availability(1L, BookAvailabilityState.STALE));

        assertTrue(fixture.cache.deepBook("OKX", "BTC-USDT").isEmpty());
        assertEquals(0, fixture.view.size());

        fixture.engine.onEvent(event(1L, 11L, "102", "103"));
        assertTrue(fixture.cache.deepBook("OKX", "BTC-USDT").isEmpty());
        assertEquals(0, fixture.view.size());
    }

    @Test
    void standaloneLiveHealthCannotRestoreInvalidatedGeneration() {
        Fixture fixture = new Fixture();
        fixture.engine.onEvent(event(1L, 10L, "100", "101"));
        fixture.engine.onAvailability(availability(1L, BookAvailabilityState.STALE));

        fixture.engine.onAvailability(availability(1L, BookAvailabilityState.LIVE));

        assertTrue(fixture.cache.deepBook("OKX", "BTC-USDT").isEmpty());
        assertEquals(0, fixture.view.size());
    }

    @Test
    void disconnectedRecoveringAndInvalidAllWithdrawTheVenue() {
        for (BookAvailabilityState state : List.of(
                BookAvailabilityState.DISCONNECTED,
                BookAvailabilityState.RECOVERING,
                BookAvailabilityState.INVALID
        )) {
            Fixture fixture = new Fixture();
            fixture.engine.onEvent(event(1L, 10L, "100", "101"));
            fixture.engine.onAvailability(availability(1L, state));
            assertTrue(fixture.cache.deepBook("OKX", "BTC-USDT").isEmpty(), state.name());
            assertEquals(0, fixture.view.size(), state.name());
        }
    }

    @Test
    void completeNewGenerationCanRestoreAfterRecoveryFence() {
        Fixture fixture = new Fixture();
        fixture.engine.onEvent(event(1L, 10L, "100", "101"));
        fixture.engine.onAvailability(availability(1L, BookAvailabilityState.RECOVERING));
        fixture.engine.onAvailability(availability(2L, BookAvailabilityState.RECOVERING));

        fixture.engine.onEvent(event(1L, 11L, "200", "201"));
        assertTrue(fixture.cache.deepBook("OKX", "BTC-USDT").isEmpty());

        fixture.engine.onEvent(event(2L, 20L, "102", "103"));
        AcceptedLocalBookEvent restored =
                fixture.cache.deepBook("OKX", "BTC-USDT").orElseThrow();
        assertEquals(2L, restored.generation());
        assertEquals(20L, restored.sequence());
        assertEquals(1, fixture.view.size());
    }

    @Test
    void engineErrorInvalidatesKnownSource() {
        Fixture fixture = new Fixture();
        fixture.engine.onEvent(event(1L, 10L, "100", "101"));

        fixture.engine.onError("okx-BTC-USDT", new IllegalStateException("bad checksum"));

        assertFalse(fixture.cache.deepBook("OKX", "BTC-USDT").isPresent());
        assertEquals(0, fixture.view.size());
    }

    private static AcceptedLocalBookEvent event(
            long generation,
            long sequence,
            String bid,
            String ask
    ) {
        long time = 1_000L + sequence;
        return new AcceptedLocalBookEvent(
                "okx-BTC-USDT",
                "OKX",
                "BTC-USDT",
                "BTC-USD",
                TransportType.WEBSOCKET,
                time,
                time,
                sequence,
                generation,
                time,
                snapshot(sequence, bid, ask, time)
        );
    }

    private static BookAvailabilityEvent availability(
            long generation,
            BookAvailabilityState state
    ) {
        return new BookAvailabilityEvent(
                "okx-BTC-USDT",
                "OKX",
                "BTC-USDT",
                "BTC-USD",
                generation,
                state,
                state.name(),
                Instant.ofEpochMilli(2_000L)
        );
    }

    private static LocalBookSnapshot snapshot(
            long sequence,
            String bid,
            String ask,
            long time
    ) {
        return new LocalBookSnapshot(
                "okx-BTC-USDT",
                "OKX",
                "BTC-USDT",
                BookQuality.LIVE,
                sequence,
                Instant.ofEpochMilli(time),
                List.of(new DecimalBookLevel(new BigDecimal(bid), BigDecimal.ONE)),
                List.of(new DecimalBookLevel(new BigDecimal(ask), BigDecimal.ONE))
        );
    }

    private static final class Fixture {
        private final MarketDataCache cache = new MarketDataCache();
        private final MarketDataEventBus bus = new MarketDataEventBus();
        private final MarketDataEngine engine = new MarketDataEngine(cache, bus);
        private final CrossExchangeBookView view = new CrossExchangeBookView(
                java.time.Duration.ofSeconds(10),
                java.time.Duration.ofMillis(250),
                () -> 2_000L
        );

        private Fixture() {
            bus.subscribe(view);
        }
    }
}

package com.example.hft.datasource.deepbook.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.hft.datasource.book.BookQuality;
import com.example.hft.datasource.engine.MarketDataCache;
import com.example.hft.datasource.engine.MarketDataEngine;
import com.example.hft.datasource.engine.MarketDataEventBus;
import com.example.hft.datasource.transport.TransportType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;


class MarketDataEngineDeepBookTest {
    @Test
    void cacheFirstThenPublishAcceptedDeepBook() {
        MarketDataCache cache = new MarketDataCache();
        MarketDataEventBus bus = new MarketDataEventBus();
        MarketDataEngine engine = new MarketDataEngine(cache, bus);
        AtomicLong observed = new AtomicLong();
        bus.subscribe(event -> {
            AcceptedLocalBookEvent accepted = (AcceptedLocalBookEvent) event;
            assertTrue(cache.deepBook(accepted.exchange(), accepted.symbol()).isPresent());
            observed.incrementAndGet();
        });

        AcceptedLocalBookEvent event = acceptedEvent();
        engine.onEvent(event);

        assertEquals(1L, observed.get());
        assertEquals(event, cache.deepBook("OKX", "BTC-USDT").orElseThrow());
        assertEquals(1, cache.deepBookCount());
    }

    @Test
    void acceptedEventRejectsNonLiveBook() {
        LocalBookSnapshot bootstrapping = snapshot(BookQuality.BOOTSTRAPPING);
        assertThrows(
                IllegalArgumentException.class,
                () -> new AcceptedLocalBookEvent(
                        "okx-BTC-USDT",
                        "OKX",
                        "BTC-USDT",
                        "BTC-USD",
                        TransportType.WEBSOCKET,
                        1L,
                        1_000L,
                        10L,
                        1L,
                        1_001L,
                        bootstrapping
                )
        );
    }

    @Test
    void publisherRequiresTransportBookSessionAndFreshness() {
        MarketDataCache cache = new MarketDataCache();
        MarketDataEngine engine = new MarketDataEngine(cache, new MarketDataEventBus());
        AtomicLong clock = new AtomicLong(1_500L);
        LocalBookPublisher publisher = new LocalBookPublisher(
                engine, 1_000L, 1, clock::get);
        LocalOrderBookBuilder builder = LocalOrderBookBuilderFactory.create(
                com.example.hft.datasource.deepbook.DeepBookSourceCatalog.okx("BTC-USDT"),
                java.time.Duration.ofSeconds(10)
        );
        BookUpdateResult result = builder.onMessage(
                """
                {"arg":{"channel":"books","instId":"BTC-USDT"},"action":"snapshot",
                 "data":[{"bids":[["100.0","1.0","0","1"]],
                 "asks":[["101.0","2.0","0","1"]],
                 "ts":"1000","seqId":10,"prevSeqId":-1}]}
                """,
                1_500L
        );
        SessionHealth health = new SessionHealth();
        health.connecting(false);
        assertFalse(publisher.publishIfEligible(builder, result, health, 1L, 1L, 1_500L));
        health.connected(1_500L);
        health.accepted(1_500L);
        assertTrue(publisher.publishIfEligible(builder, result, health, 1L, 1L, 1_500L));
        assertEquals(1, cache.deepBookCount());
        clock.set(2_501L);
        assertFalse(publisher.publishIfEligible(builder, result, health, 1L, 1L, 1_500L));
        health.stopped();
        assertFalse(publisher.publishIfEligible(builder, result, health, 1L, 1L, 1_500L));
    }

    private static AcceptedLocalBookEvent acceptedEvent() {
        return new AcceptedLocalBookEvent(
                "okx-BTC-USDT",
                "OKX",
                "BTC-USDT",
                "BTC-USD",
                TransportType.WEBSOCKET,
                1L,
                1_000L,
                10L,
                1L,
                1_001L,
                snapshot(BookQuality.LIVE)
        );
    }

    private static LocalBookSnapshot snapshot(BookQuality quality) {
        return new LocalBookSnapshot(
                "okx-BTC-USDT",
                "OKX",
                "BTC-USDT",
                quality,
                10L,
                Instant.ofEpochMilli(1_000L),
                List.of(new DecimalBookLevel(new BigDecimal("100.0"), new BigDecimal("1.0"))),
                List.of(new DecimalBookLevel(new BigDecimal("101.0"), new BigDecimal("2.0")))
        );
    }
}

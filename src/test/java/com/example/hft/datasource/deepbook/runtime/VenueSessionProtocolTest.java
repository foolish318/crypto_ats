package com.example.hft.datasource.deepbook.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.hft.datasource.deepbook.DeepBookSourceCatalog;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;


class VenueSessionProtocolTest {
    @Test
    void requiresAckThenDetectsPongTimeout() {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        AtomicLong clock = new AtomicLong(1_000L);
        List<Long> pings = new ArrayList<>();
        List<String> recoveries = new ArrayList<>();
        try (VenueSessionProtocol protocol = new VenueSessionProtocol(
                scheduler,
                DeepBookSourceCatalog.okx("BTC-USDT"),
                Duration.ofMillis(100),
                Duration.ofMillis(50),
                Duration.ofSeconds(1),
                Duration.ZERO,
                pings::add,
                recoveries::add,
                clock::get,
                new ObjectMapper()
        )) {
            protocol.connected(7L);
            assertFalse(protocol.bookDataAllowed());

            protocol.onText("{\"event\":\"subscribe\",\"code\":\"0\"}");
            assertTrue(protocol.bookDataAllowed());

            clock.set(1_101L);
            protocol.check();
            assertEquals(List.of(7L), pings);
            assertTrue(protocol.snapshot().awaitingPong());

            clock.set(1_152L);
            protocol.check();
            assertEquals(1, recoveries.size());
            assertTrue(recoveries.get(0).contains("pong timeout"));
            assertFalse(protocol.bookDataAllowed());
        } finally {
            scheduler.shutdownNow();
        }
    }

    @Test
    void missingSubscriptionAckTriggersRecovery() {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        AtomicLong clock = new AtomicLong(10_000L);
        List<String> recoveries = new ArrayList<>();
        try (VenueSessionProtocol protocol = new VenueSessionProtocol(
                scheduler,
                DeepBookSourceCatalog.kraken("BTC/USD"),
                Duration.ofSeconds(5),
                Duration.ofSeconds(2),
                Duration.ofMillis(100),
                Duration.ZERO,
                ignored -> { },
                recoveries::add,
                clock::get,
                new ObjectMapper()
        )) {
            protocol.connected(3L);
            clock.set(10_101L);
            protocol.check();

            assertEquals(1, recoveries.size());
            assertTrue(recoveries.get(0).contains("ACK timeout"));
            assertEquals(1L, protocol.snapshot().subscriptionErrors());
            assertFalse(protocol.bookDataAllowed());
        } finally {
            scheduler.shutdownNow();
        }
    }
}
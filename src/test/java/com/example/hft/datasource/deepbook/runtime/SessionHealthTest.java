package com.example.hft.datasource.deepbook.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;


class SessionHealthTest {
    @Test
    void threeIndependentStatesGatePublicationAndStopIsTerminal() {
        SessionHealth health = new SessionHealth();
        health.connecting(false);
        assertFalse(health.publishable(1_000L, 1_000L));

        health.connected(1_000L);
        health.bookState(BookState.BOOTSTRAPPING);
        assertFalse(health.publishable(1_100L, 1_000L));

        health.accepted(1_200L);
        assertTrue(health.publishable(1_500L, 1_000L));

        health.stopped();
        health.connecting(true);
        health.connected(1_550L);
        health.recovering("late callback");
        health.accepted(1_550L);
        SessionHealthSnapshot stopped = health.snapshot(1_600L, 1_000L);
        assertEquals(TransportState.DISCONNECTED, stopped.transportState());
        assertEquals(SessionState.STOPPED, stopped.sessionState());
        assertFalse(stopped.publishable(1_000L));
    }

    @Test
    void watchdogMarksSilentConnectedSessionStaleAndRequestsRecovery() {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        try {
            SessionHealth health = new SessionHealth();
            health.connecting(false);
            health.connected(1_000L);
            health.bookState(BookState.LIVE);
            health.accepted(1_000L);
            AtomicReference<String> reason = new AtomicReference<>();
            try (StaleWatchdog watchdog = new StaleWatchdog(
                    scheduler,
                    health,
                    Duration.ofMillis(500L),
                    reason::set,
                    () -> 1_600L
            )) {
                watchdog.check();
            }

            SessionHealthSnapshot snapshot = health.snapshot(1_600L, 500L);
            assertEquals(BookState.STALE, snapshot.bookState());
            assertEquals(SessionState.DEGRADED, snapshot.sessionState());
            assertEquals(1L, snapshot.staleTransitions());
            assertTrue(reason.get().contains("no messages"));
        } finally {
            scheduler.shutdownNow();
        }
    }
}

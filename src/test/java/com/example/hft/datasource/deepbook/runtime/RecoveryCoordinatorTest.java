package com.example.hft.datasource.deepbook.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;


class RecoveryCoordinatorTest {
    @Test
    void exponentialBackoffUsesExpectedBaseAndCap() {
        assertEquals(300L, RecoveryCoordinator.calculateDelayMillis(0, 0.5));
        assertEquals(600L, RecoveryCoordinator.calculateDelayMillis(1, 0.5));
        assertEquals(1_200L, RecoveryCoordinator.calculateDelayMillis(2, 0.5));
        assertEquals(2_400L, RecoveryCoordinator.calculateDelayMillis(3, 0.5));
        assertEquals(30_000L, RecoveryCoordinator.calculateDelayMillis(20, 0.5));
    }

    @Test
    void rapidReconnectFailureSchedulesNextAttempt() throws Exception {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        SessionHealth health = new SessionHealth();
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch secondAttempt = new CountDownLatch(1);
        AtomicReference<RecoveryCoordinator> holder = new AtomicReference<>();
        RecoveryCoordinator coordinator = new RecoveryCoordinator(
                scheduler,
                health,
                ignored -> {
                    int call = calls.incrementAndGet();
                    if (call == 1) {
                        holder.get().connectFailed("fast failure");
                    } else {
                        secondAttempt.countDown();
                    }
                },
                () -> 0.5
        );
        holder.set(coordinator);
        try {
            assertTrue(coordinator.requestRecovery("test"));
            assertTrue(secondAttempt.await(2L, TimeUnit.SECONDS));
            RecoverySnapshot snapshot = coordinator.snapshot();
            assertEquals(2L, snapshot.reconnectAttempts());
            assertEquals(1L, snapshot.reconnectFailures());
            assertEquals(600L, snapshot.nextBackoffMillis());
        } finally {
            coordinator.close();
            scheduler.shutdownNow();
        }
    }

    @Test
    void stopCancelsPendingReconnect() throws Exception {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        AtomicInteger calls = new AtomicInteger();
        RecoveryCoordinator coordinator = new RecoveryCoordinator(
                scheduler,
                new SessionHealth(),
                ignored -> calls.incrementAndGet(),
                () -> 0.5
        );
        assertTrue(coordinator.requestRecovery("test"));
        coordinator.close();
        Thread.sleep(350L);
        assertEquals(0, calls.get());
        assertFalse(coordinator.requestRecovery("after stop"));
        scheduler.shutdownNow();
    }
}

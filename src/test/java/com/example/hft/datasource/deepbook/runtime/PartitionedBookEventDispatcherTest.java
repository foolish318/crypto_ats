package com.example.hft.datasource.deepbook.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;


class PartitionedBookEventDispatcherTest {
    @Test
    void preservesSourceOrderAndDrainsBeforePause() throws Exception {
        List<Integer> observed = Collections.synchronizedList(new ArrayList<>());
        try (PartitionedBookEventDispatcher dispatcher =
                     new PartitionedBookEventDispatcher(List.of("a", "b"), 2, 128)) {
            for (int i = 0; i < 100; i++) {
                int value = i;
                assertEquals(DispatchResult.ACCEPTED,
                        dispatcher.submit("a", () -> observed.add(value)));
            }
            dispatcher.pause("a");
            assertEquals(DispatchResult.PAUSED, dispatcher.submit("a", () -> { }));
            assertTrue(dispatcher.awaitSourceDrained("a", Duration.ofSeconds(2)));
            assertEquals(100, observed.size());
            for (int i = 0; i < observed.size(); i++) {
                assertEquals(i, observed.get(i));
            }
        }
    }

    @Test
    void reportsQueueFullAndContinuesAfterTaskFailure() throws Exception {
        CountDownLatch running = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch failureSeen = new CountDownLatch(1);
        AtomicInteger completedAfterFailure = new AtomicInteger();
        try (PartitionedBookEventDispatcher dispatcher =
                     new PartitionedBookEventDispatcher(List.of("a"), 1, 1)) {
            assertEquals(DispatchResult.ACCEPTED, dispatcher.submit("a", () -> {
                running.countDown();
                try {
                    release.await();
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                }
            }));
            assertTrue(running.await(1, TimeUnit.SECONDS));
            assertEquals(DispatchResult.ACCEPTED, dispatcher.submit("a", () -> {
                throw new IllegalStateException("injected");
            }, ignored -> failureSeen.countDown()));
            assertEquals(DispatchResult.FULL,
                    dispatcher.submit("a", completedAfterFailure::incrementAndGet));
            release.countDown();
            assertTrue(failureSeen.await(1, TimeUnit.SECONDS));
            assertTrue(dispatcher.awaitSourceDrained("a", Duration.ofSeconds(2)));

            assertEquals(DispatchResult.ACCEPTED,
                    dispatcher.submit("a", completedAfterFailure::incrementAndGet));
            assertTrue(dispatcher.awaitSourceDrained("a", Duration.ofSeconds(2)));
            assertEquals(1, completedAfterFailure.get());
            assertEquals(1L, dispatcher.snapshot().queueFullRejections());
        }
    }
}
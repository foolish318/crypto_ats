package com.example.hft.datasource.deepbook.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.hft.datasource.engine.AsyncListenerSnapshot;
import com.example.hft.datasource.engine.MarketDataEventBus;
import com.example.hft.datasource.normalizer.NormalizedMarketDataEvent;
import com.example.hft.datasource.transport.TransportType;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;


class MarketDataEventBusIsolationTest {
    @Test
    void oneCoreListenerFailureDoesNotBlockTheNextListener() {
        try (MarketDataEventBus bus = new MarketDataEventBus()) {
            AtomicLong delivered = new AtomicLong();
            bus.subscribe(event -> {
                throw new IllegalStateException("listener failed");
            });
            bus.subscribe(event -> delivered.incrementAndGet());

            bus.publish(event(1L));

            assertEquals(1L, delivered.get());
            assertEquals(1L, bus.coreListenerErrors());
        }
    }

    @Test
    void slowAsyncListenerIsBoundedAndReportsDropAndLag() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (MarketDataEventBus bus = new MarketDataEventBus()) {
            bus.subscribeAsync("slow", event -> {
                entered.countDown();
                try {
                    release.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            }, 1);

            bus.publish(event(1L));
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            bus.publish(event(2L));
            bus.publish(event(3L));

            AsyncListenerSnapshot snapshot = bus.asyncSnapshots().get(0);
            assertTrue(snapshot.queueDepth() <= 1);
            assertTrue(snapshot.droppedEvents() >= 1L);
            assertTrue(snapshot.maxQueueDepth() <= 1);
            release.countDown();
        }
    }

    @Test
    void asyncListenerExceptionIsRecordedWithoutEscapingPublisher() throws Exception {
        try (MarketDataEventBus bus = new MarketDataEventBus()) {
            bus.subscribeAsync("throwing", event -> {
                throw new IllegalArgumentException("bad side output");
            }, 4);

            bus.publish(event(1L));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (bus.asyncSnapshots().get(0).errors() == 0L
                    && System.nanoTime() < deadline) {
                Thread.sleep(5L);
            }

            AsyncListenerSnapshot snapshot = bus.asyncSnapshots().get(0);
            assertEquals(1L, snapshot.errors());
            assertTrue(snapshot.lastError().contains("bad side output"));
        }
    }

    private static TestEvent event(long sequence) {
        return new TestEvent(
                "test",
                "TEST",
                "BTC-USD",
                TransportType.REPLAY,
                System.nanoTime(),
                sequence,
                sequence
        );
    }

    private record TestEvent(
            String source,
            String exchange,
            String symbol,
            TransportType transport,
            long receivedNanos,
            long exchangeTimeMillis,
            long sequence
    ) implements NormalizedMarketDataEvent {
    }
}

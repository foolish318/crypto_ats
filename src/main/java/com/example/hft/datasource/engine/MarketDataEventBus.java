package com.example.hft.datasource.engine;

import com.example.hft.datasource.deepbook.runtime.BookAvailabilityEvent;
import com.example.hft.datasource.normalizer.NormalizedMarketDataEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;


public final class MarketDataEventBus implements AutoCloseable {
    private final List<CoreRegistration> coreListeners = new CopyOnWriteArrayList<>();
    private final List<AsyncRegistration> asyncListeners = new CopyOnWriteArrayList<>();
    private final AtomicBoolean closed = new AtomicBoolean();

    public void subscribe(MarketDataListener listener) {
        requireOpen();
        coreListeners.add(new CoreRegistration(listener));
    }

    public void subscribeAsync(String name, MarketDataListener listener, int capacity) {
        requireOpen();
        if (name == null || name.isBlank() || capacity <= 0) {
            throw new IllegalArgumentException("async listener name and positive capacity required");
        }
        asyncListeners.add(new AsyncRegistration(name, listener, capacity));
    }

    public void publish(NormalizedMarketDataEvent event) {
        publishMeasured(event);
    }

    public EventBusPublishResult publishMeasured(NormalizedMarketDataEvent event) {
        Objects.requireNonNull(event, "event");
        return publish(Dispatch.marketData(event));
    }

    public void publishAvailability(BookAvailabilityEvent event) {
        publishAvailabilityMeasured(event);
    }

    public EventBusPublishResult publishAvailabilityMeasured(BookAvailabilityEvent event) {
        Objects.requireNonNull(event, "event");
        return publish(Dispatch.availability(event));
    }

    private EventBusPublishResult publish(Dispatch dispatch) {
        long coreStarted = System.nanoTime();
        for (CoreRegistration registration : coreListeners) {
            registration.dispatch(dispatch);
        }
        long coreNanos = System.nanoTime() - coreStarted;
        long asyncStarted = System.nanoTime();
        for (AsyncRegistration registration : asyncListeners) {
            registration.offer(dispatch);
        }
        return new EventBusPublishResult(
                coreNanos,
                System.nanoTime() - asyncStarted
        );
    }

    public int listenerCount() {
        return coreListeners.size() + asyncListeners.size();
    }

    public long coreListenerErrors() {
        return coreListeners.stream().mapToLong(item -> item.errors.get()).sum();
    }

    public List<AsyncListenerSnapshot> asyncSnapshots() {
        List<AsyncListenerSnapshot> snapshots = new ArrayList<>(asyncListeners.size());
        for (AsyncRegistration listener : asyncListeners) {
            snapshots.add(listener.snapshot());
        }
        return List.copyOf(snapshots);
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        for (AsyncRegistration listener : asyncListeners) {
            listener.close();
        }
    }

    private void requireOpen() {
        if (closed.get()) {
            throw new IllegalStateException("event bus is closed");
        }
    }

    private static final class CoreRegistration {
        private final MarketDataListener listener;
        private final AtomicLong errors = new AtomicLong();

        private CoreRegistration(MarketDataListener listener) {
            this.listener = Objects.requireNonNull(listener, "listener");
        }

        private void dispatch(Dispatch dispatch) {
            try {
                dispatch.deliver(listener);
            } catch (Throwable error) {
                errors.incrementAndGet();
            }
        }
    }

    private static final class AsyncRegistration implements AutoCloseable {
        private final String name;
        private final MarketDataListener listener;
        private final ArrayBlockingQueue<QueuedDispatch> queue;
        private final int capacity;
        private final AtomicBoolean running = new AtomicBoolean(true);
        private final AtomicLong accepted = new AtomicLong();
        private final AtomicLong dropped = new AtomicLong();
        private final AtomicLong errors = new AtomicLong();
        private final AtomicLong lastLagNanos = new AtomicLong();
        private final AtomicLong maxLagNanos = new AtomicLong();
        private final AtomicInteger maxDepth = new AtomicInteger();
        private final AtomicReference<String> lastError = new AtomicReference<>("");
        private final Thread worker;

        private AsyncRegistration(String name, MarketDataListener listener, int capacity) {
            this.name = name;
            this.listener = Objects.requireNonNull(listener, "listener");
            this.capacity = capacity;
            this.queue = new ArrayBlockingQueue<>(capacity);
            this.worker = new Thread(this::run, "market-data-side-output-" + name);
            this.worker.start();
        }

        private void offer(Dispatch dispatch) {
            QueuedDispatch queued = new QueuedDispatch(dispatch, System.nanoTime());
            if (!running.get() || !queue.offer(queued)) {
                dropped.incrementAndGet();
                return;
            }
            accepted.incrementAndGet();
            maxDepth.accumulateAndGet(queue.size(), Math::max);
        }

        private void run() {
            try {
                while (running.get() || !queue.isEmpty()) {
                    QueuedDispatch queued = queue.poll(100L, TimeUnit.MILLISECONDS);
                    if (queued == null) {
                        continue;
                    }
                    long lag = Math.max(0L, System.nanoTime() - queued.enqueuedNanos);
                    lastLagNanos.set(lag);
                    maxLagNanos.accumulateAndGet(lag, Math::max);
                    try {
                        queued.dispatch.deliver(listener);
                    } catch (Throwable error) {
                        lastError.set(error.getClass().getSimpleName() + ": " + error.getMessage());
                        errors.incrementAndGet();
                    }
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                if (running.get()) {
                    errors.incrementAndGet();
                    lastError.set("InterruptedException: async listener interrupted");
                }
            }
        }

        private AsyncListenerSnapshot snapshot() {
            return new AsyncListenerSnapshot(
                    name, capacity, queue.size(), maxDepth.get(), accepted.get(), dropped.get(),
                    errors.get(), lastLagNanos.get(), maxLagNanos.get(), lastError.get()
            );
        }

        @Override
        public void close() {
            running.set(false);
            try {
                worker.join(10_000L);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                errors.incrementAndGet();
                lastError.set("InterruptedException: close interrupted");
            }
            if (worker.isAlive()) {
                worker.interrupt();
                errors.incrementAndGet();
                lastError.set("async listener did not drain before close");
            }
        }
    }

    private record QueuedDispatch(Dispatch dispatch, long enqueuedNanos) {
    }

    private record Dispatch(
            NormalizedMarketDataEvent marketData,
            BookAvailabilityEvent availability
    ) {
        private static Dispatch marketData(NormalizedMarketDataEvent event) {
            return new Dispatch(event, null);
        }

        private static Dispatch availability(BookAvailabilityEvent event) {
            return new Dispatch(null, event);
        }

        private void deliver(MarketDataListener listener) {
            if (marketData != null) {
                listener.onMarketData(marketData);
            } else {
                listener.onBookAvailability(availability);
            }
        }
    }
}

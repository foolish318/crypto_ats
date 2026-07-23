package com.example.hft.datasource.deepbook.runtime;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;


public final class PartitionedBookEventDispatcher implements AutoCloseable {
    private final ArrayBlockingQueue<Task>[] queues;
    private final Thread[] threads;
    private final Map<String, Integer> partitions;
    private final Set<String> paused = ConcurrentHashMap.newKeySet();
    private final Map<String, AtomicLong> pendingBySource = new ConcurrentHashMap<>();
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicLong submitted = new AtomicLong();
    private final AtomicLong completed = new AtomicLong();
    private final AtomicLong queueFullRejections = new AtomicLong();
    private final AtomicLong taskFailures = new AtomicLong();
    private final AtomicLong maxQueueDepth = new AtomicLong();
    private final AtomicLong queueWaitNanos = new AtomicLong();
    private final AtomicLong maxQueueWaitNanos = new AtomicLong();
    private final AtomicLong processingNanos = new AtomicLong();
    private final AtomicLong maxProcessingNanos = new AtomicLong();
    private final int queueCapacity;

    @SuppressWarnings("unchecked")
    public PartitionedBookEventDispatcher(
            List<String> sourceIds,
            int workerCount,
            int queueCapacity
    ) {
        if (sourceIds.isEmpty() || workerCount <= 0 || queueCapacity <= 0) {
            throw new IllegalArgumentException(
                    "sources, workerCount, and queueCapacity must be positive");
        }
        if (new HashSet<>(sourceIds).size() != sourceIds.size()) {
            throw new IllegalArgumentException("source IDs must be unique");
        }
        this.queueCapacity = queueCapacity;
        this.partitions = assignRoundRobin(sourceIds, workerCount);
        this.queues = new ArrayBlockingQueue[workerCount];
        this.threads = new Thread[workerCount];
        for (String sourceId : sourceIds) {
            pendingBySource.put(sourceId, new AtomicLong());
        }
        for (int i = 0; i < workerCount; i++) {
            queues[i] = new ArrayBlockingQueue<>(queueCapacity);
            int partition = i;
            threads[i] = new Thread(() -> run(partition), "deep-book-live-" + i);
            threads[i].start();
        }
    }

    public DispatchResult submit(String sourceId, Runnable action) {
        return submit(sourceId, action, ignored -> { });
    }

    public DispatchResult submit(
            String sourceId,
            Runnable action,
            Consumer<Throwable> failureHandler
    ) {
        Integer partition = partitions.get(sourceId);
        if (partition == null) {
            throw new IllegalArgumentException("unknown dispatcher source " + sourceId);
        }
        if (!running.get()) {
            return DispatchResult.STOPPED;
        }
        if (paused.contains(sourceId)) {
            return DispatchResult.PAUSED;
        }
        AtomicLong pending = pendingBySource.get(sourceId);
        pending.incrementAndGet();
        Task task = new Task(sourceId, action, failureHandler, System.nanoTime(), false);
        if (!queues[partition].offer(task)) {
            pending.decrementAndGet();
            queueFullRejections.incrementAndGet();
            return DispatchResult.FULL;
        }
        submitted.incrementAndGet();
        maxQueueDepth.accumulateAndGet(queues[partition].size(), Math::max);
        return DispatchResult.ACCEPTED;
    }

    public void pause(String sourceId) {
        requireSource(sourceId);
        paused.add(sourceId);
    }

    public void resume(String sourceId) {
        requireSource(sourceId);
        if (running.get()) {
            paused.remove(sourceId);
        }
    }

    public boolean awaitSourceDrained(String sourceId, Duration timeout)
            throws InterruptedException {
        AtomicLong pending = requireSource(sourceId);
        long deadline = System.nanoTime() + timeout.toNanos();
        while (pending.get() != 0L && System.nanoTime() < deadline) {
            Thread.sleep(1L);
        }
        return pending.get() == 0L;
    }

    public DispatcherSnapshot snapshot() {
        return new DispatcherSnapshot(
                threads.length,
                queueCapacity,
                submitted.get(),
                completed.get(),
                queueFullRejections.get(),
                taskFailures.get(),
                maxQueueDepth.get(),
                averageMicros(queueWaitNanos.get(), completed.get()),
                maxQueueWaitNanos.get() / 1_000.0,
                averageMicros(processingNanos.get(), completed.get()),
                maxProcessingNanos.get() / 1_000.0
        );
    }

    @Override
    public void close() throws InterruptedException {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        paused.addAll(partitions.keySet());
        for (ArrayBlockingQueue<Task> queue : queues) {
            queue.put(Task.stop());
        }
        for (Thread thread : threads) {
            thread.join(TimeUnit.SECONDS.toMillis(10));
            if (thread.isAlive()) {
                thread.interrupt();
                throw new IllegalStateException("book dispatcher did not stop");
            }
        }
    }

    private void run(int partition) {
        try {
            while (true) {
                Task task = queues[partition].take();
                if (task.terminal()) {
                    return;
                }
                long processingStarted = System.nanoTime();
                long queueNanos = processingStarted - task.enqueuedNanos();
                queueWaitNanos.addAndGet(queueNanos);
                maxQueueWaitNanos.accumulateAndGet(queueNanos, Math::max);
                try {
                    task.action().run();
                } catch (Throwable error) {
                    taskFailures.incrementAndGet();
                    try {
                        task.failureHandler().accept(error);
                    } catch (Throwable ignored) {
                        // Keep the source partition alive; the failure remains observable.
                    }
                } finally {
                    long taskNanos = System.nanoTime() - processingStarted;
                    processingNanos.addAndGet(taskNanos);
                    maxProcessingNanos.accumulateAndGet(taskNanos, Math::max);
                    completed.incrementAndGet();
                    pendingBySource.get(task.sourceId()).decrementAndGet();
                }
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private AtomicLong requireSource(String sourceId) {
        AtomicLong pending = pendingBySource.get(sourceId);
        if (pending == null) {
            throw new IllegalArgumentException("unknown dispatcher source " + sourceId);
        }
        return pending;
    }

    private static Map<String, Integer> assignRoundRobin(
            List<String> sourceIds,
            int workers
    ) {
        Map<String, Integer> result = new HashMap<>();
        for (int i = 0; i < sourceIds.size(); i++) {
            result.put(sourceIds.get(i), i % workers);
        }
        return Map.copyOf(result);
    }

    private static double averageMicros(long totalNanos, long count) {
        return count == 0L ? 0.0 : (double) totalNanos / count / 1_000.0;
    }
    private record Task(
            String sourceId,
            Runnable action,
            Consumer<Throwable> failureHandler,
            long enqueuedNanos,
            boolean terminal
    ) {
        private static Task stop() {
            return new Task("", () -> { }, ignored -> { }, 0L, true);
        }
    }
}
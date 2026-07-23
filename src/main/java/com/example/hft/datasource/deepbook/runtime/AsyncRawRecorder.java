package com.example.hft.datasource.deepbook.runtime;

import com.example.hft.datasource.DataSourceModuleVersion;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;


public final class AsyncRawRecorder implements AutoCloseable {
    public static final int DEFAULT_QUEUE_CAPACITY = 65_536;

    private final ArrayBlockingQueue<QueuedEnvelope> queue;
    private final AtomicLong recorded = new AtomicLong();
    private final AtomicLong dropped = new AtomicLong();
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicBoolean inFlight = new AtomicBoolean();
    private final AtomicInteger maxQueueDepth = new AtomicInteger();
    private final AtomicReference<RawEnvelope> firstDropped = new AtomicReference<>();
    private final AtomicReference<String> firstDropReason = new AtomicReference<>();
    private final RawJournalWriter journal;
    private final Thread writerThread;
    private final int queueCapacity;
    private volatile Throwable failure;

    public AsyncRawRecorder(Path path, ObjectMapper mapper) throws Exception {
        this(path, mapper, DEFAULT_QUEUE_CAPACITY, RawJournalConfig.defaults());
    }

    public AsyncRawRecorder(Path path, ObjectMapper mapper, int queueCapacity) throws Exception {
        this(path, mapper, queueCapacity, RawJournalConfig.defaults());
    }

    public AsyncRawRecorder(
            Path path,
            ObjectMapper mapper,
            int queueCapacity,
            RawJournalConfig config
    ) throws Exception {
        if (queueCapacity <= 0) {
            throw new IllegalArgumentException("queueCapacity must be positive");
        }
        this.queueCapacity = queueCapacity;
        this.queue = new ArrayBlockingQueue<>(queueCapacity);
        this.journal = new RawJournalWriter(path, mapper, config);
        this.writerThread = new Thread(this::writeLoop, "deep-book-raw-recorder");
        this.writerThread.start();
    }

    public boolean record(RawEnvelope envelope) {
        if (!running.get()) {
            noteDrop(envelope, "recorder is stopped");
            return false;
        }
        QueuedEnvelope queued = new QueuedEnvelope(envelope, System.nanoTime());
        if (queue.offer(queued)) {
            maxQueueDepth.accumulateAndGet(queue.size(), Math::max);
            return true;
        }
        noteDrop(envelope, "recorder queue capacity " + queueCapacity + " exceeded");
        return false;
    }

    public void markReplayUnsafe(RawEnvelope envelope, String reason) {
        noteDrop(envelope, reason);
    }

    public RawRecorderSummary summary() {
        RawEnvelope first = firstDropped.get();
        Throwable currentFailure = failure;
        return new RawRecorderSummary(
                recorded.get(),
                dropped.get(),
                dropped.get() == 0L && currentFailure == null,
                first == null ? 0L : first.receivedEpochMillis(),
                first == null ? "" : firstDropReason.get()
                        + " for " + first.recordType()
                        + " source=" + first.sourceId()
                        + " generation=" + first.generation(),
                currentFailure == null
                        ? ""
                        : currentFailure.getClass().getSimpleName()
                                + ": " + currentFailure.getMessage(),
                queueCapacity,
                queue.size(),
                maxQueueDepth.get(),
                journal.lastWriteLagNanos(),
                journal.maxWriteLagNanos(),
                journal.currentSegment(),
                journal.diskUsageBytes()
        );
    }

    public boolean awaitDrained(long timeoutMillis) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while ((!queue.isEmpty() || inFlight.get()) && System.nanoTime() < deadline) {
            Thread.sleep(5L);
        }
        return queue.isEmpty() && !inFlight.get();
    }

    private void noteDrop(RawEnvelope envelope, String reason) {
        dropped.incrementAndGet();
        if (firstDropped.compareAndSet(null, envelope)) {
            firstDropReason.set(reason);
        }
    }

    private void writeLoop() {
        try {
            while (running.get() || !queue.isEmpty()) {
                QueuedEnvelope queued = queue.poll(100, TimeUnit.MILLISECONDS);
                if (queued == null) {
                    continue;
                }
                inFlight.set(true);
                try {
                    journal.write(queued.envelope(), queued.enqueuedNanos());
                    recorded.incrementAndGet();
                } finally {
                    inFlight.set(false);
                }
            }
            RawEnvelope first = firstDropped.get();
            if (first != null) {
                RawEnvelope marker = new RawEnvelope(
                        DataSourceModuleVersion.VERSION,
                        RawRecordType.RECOVERY,
                        first.generation(),
                        first.sourceId(),
                        first.exchange(),
                        first.symbol(),
                        System.currentTimeMillis(),
                        System.nanoTime(),
                        "",
                        "REPLAY_UNSAFE dropped " + dropped.get()
                                + " records; firstDropEpochMillis=" + first.receivedEpochMillis()
                                + " reason=" + firstDropReason.get()
                );
                journal.write(marker, System.nanoTime());
                recorded.incrementAndGet();
            }
        } catch (Throwable error) {
            failure = error;
        }
    }

    @Override
    public void close() throws Exception {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        running.set(false);
        writerThread.join(10_000L);
        if (writerThread.isAlive()) {
            writerThread.interrupt();
            throw new IllegalStateException("raw recorder did not stop");
        }
        journal.close();
        if (failure != null) {
            throw new IllegalStateException("raw recorder failed", failure);
        }
    }

    private record QueuedEnvelope(RawEnvelope envelope, long enqueuedNanos) {
    }
}

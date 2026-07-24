package com.example.hft.marketdata.recording;

import com.example.hft.marketdata.model.BookSnapshot;
import com.example.hft.marketdata.model.BookStatusChange;
import com.example.hft.marketdata.model.PublicTrade;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public final class AsyncNormalizedEventRecorder
        implements NormalizedEventSink, AutoCloseable {
    public static final int DEFAULT_CAPACITY = 65_536;
    private static final int FLUSH_EVERY = 256;

    private final ArrayBlockingQueue<QueuedRecord> queue;
    private final ObjectMapper mapper;
    private final BufferedWriter writer;
    private final Thread writerThread;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicLong nextOrdinal = new AtomicLong();
    private final AtomicLong recorded = new AtomicLong();
    private final AtomicLong dropped = new AtomicLong();
    private final AtomicInteger maxQueueDepth = new AtomicInteger();
    private final AtomicLong lastWriteLag = new AtomicLong();
    private final AtomicLong maxWriteLag = new AtomicLong();
    private final AtomicReference<Throwable> failure = new AtomicReference<>();

    public AsyncNormalizedEventRecorder(Path path, ObjectMapper mapper) throws Exception {
        this(path, mapper, DEFAULT_CAPACITY);
    }

    public AsyncNormalizedEventRecorder(Path path, ObjectMapper mapper, int capacity) throws Exception {
        if (path == null || mapper == null || capacity <= 0) {
            throw new IllegalArgumentException("path, mapper, and positive capacity are required");
        }
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        this.queue = new ArrayBlockingQueue<>(capacity);
        this.mapper = mapper;
        this.writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8);
        this.writerThread = new Thread(this::writeLoop, "normalized-market-event-recorder");
        this.writerThread.start();
    }

    @Override
    public synchronized boolean recordBook(BookSnapshot book) {
        return offer(NormalizedEventRecord.book(nextOrdinal.incrementAndGet(), book));
    }

    @Override
    public synchronized boolean recordTrade(PublicTrade trade) {
        return offer(NormalizedEventRecord.trade(nextOrdinal.incrementAndGet(), trade));
    }

    @Override
    public synchronized boolean recordStatus(BookStatusChange status) {
        return offer(NormalizedEventRecord.status(nextOrdinal.incrementAndGet(), status));
    }

    private boolean offer(NormalizedEventRecord record) {
        if (!running.get() || failure.get() != null) {
            dropped.incrementAndGet();
            return false;
        }
        boolean accepted = queue.offer(new QueuedRecord(record, System.nanoTime()));
        if (!accepted) {
            dropped.incrementAndGet();
            return false;
        }
        maxQueueDepth.accumulateAndGet(queue.size(), Math::max);
        return true;
    }

    public boolean awaitDrained(long timeoutMillis) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (!queue.isEmpty() && System.nanoTime() < deadline) {
            Thread.sleep(5L);
        }
        return queue.isEmpty();
    }

    public NormalizedRecorderSummary summary() {
        Throwable error = failure.get();
        return new NormalizedRecorderSummary(
                recorded.get(), dropped.get(), dropped.get() == 0L && error == null,
                queue.size(), maxQueueDepth.get(), lastWriteLag.get(), maxWriteLag.get(),
                error == null ? "" : error.getClass().getSimpleName() + ": " + error.getMessage()
        );
    }

    private void writeLoop() {
        int sinceFlush = 0;
        try {
            while (running.get() || !queue.isEmpty()) {
                QueuedRecord queued = queue.poll(100L, TimeUnit.MILLISECONDS);
                if (queued == null) {
                    continue;
                }
                long lag = Math.max(0L, System.nanoTime() - queued.enqueuedNanos());
                lastWriteLag.set(lag);
                maxWriteLag.accumulateAndGet(lag, Math::max);
                writer.write(mapper.writeValueAsString(queued.record()));
                writer.newLine();
                recorded.incrementAndGet();
                sinceFlush++;
                if (sinceFlush >= FLUSH_EVERY) {
                    writer.flush();
                    sinceFlush = 0;
                }
            }
            writer.flush();
        } catch (Throwable error) {
            failure.compareAndSet(null, error);
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
            throw new IllegalStateException("normalized recorder did not stop");
        }
        writer.close();
        Throwable error = failure.get();
        if (error != null) {
            throw new IllegalStateException("normalized recorder failed", error);
        }
    }

    private record QueuedRecord(NormalizedEventRecord record, long enqueuedNanos) {
    }
}
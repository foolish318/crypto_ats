package com.example.hft.datasource.deepbook.runtime;

import com.example.hft.datasource.DataSourceModuleVersion;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;


public final class AsyncRawRecorder implements AutoCloseable {
    public static final int DEFAULT_QUEUE_CAPACITY = 65_536;

    private final ArrayBlockingQueue<RawEnvelope> queue;
    private final AtomicLong recorded = new AtomicLong();
    private final AtomicLong dropped = new AtomicLong();
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicReference<RawEnvelope> firstDropped = new AtomicReference<>();
    private final BufferedWriter writer;
    private final ObjectMapper mapper;
    private final Thread writerThread;
    private final int queueCapacity;
    private volatile Throwable failure;

    public AsyncRawRecorder(Path path, ObjectMapper mapper) throws Exception {
        this(path, mapper, DEFAULT_QUEUE_CAPACITY);
    }

    public AsyncRawRecorder(Path path, ObjectMapper mapper, int queueCapacity) throws Exception {
        if (queueCapacity <= 0) {
            throw new IllegalArgumentException("queueCapacity must be positive");
        }
        this.queueCapacity = queueCapacity;
        this.queue = new ArrayBlockingQueue<>(queueCapacity);
        this.writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8);
        this.mapper = mapper;
        this.writerThread = new Thread(this::writeLoop, "deep-book-raw-recorder");
        this.writerThread.start();
    }

    public boolean record(RawEnvelope envelope) {
        if (!running.get()) {
            noteDrop(envelope);
            return false;
        }
        if (queue.offer(envelope)) {
            return true;
        }
        noteDrop(envelope);
        return false;
    }

    public RawRecorderSummary summary() {
        RawEnvelope first = firstDropped.get();
        Throwable currentFailure = failure;
        return new RawRecorderSummary(
                recorded.get(),
                dropped.get(),
                dropped.get() == 0L && currentFailure == null,
                first == null ? 0L : first.receivedEpochMillis(),
                first == null ? "" : "queue capacity " + queueCapacity
                        + " exceeded for " + first.recordType()
                        + " source=" + first.sourceId()
                        + " generation=" + first.generation(),
                currentFailure == null
                        ? ""
                        : currentFailure.getClass().getSimpleName() + ": " + currentFailure.getMessage()
        );
    }

    public boolean awaitDrained(long timeoutMillis) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (!queue.isEmpty() && System.nanoTime() < deadline) {
            Thread.sleep(5L);
        }
        return queue.isEmpty();
    }

    private void noteDrop(RawEnvelope envelope) {
        dropped.incrementAndGet();
        firstDropped.compareAndSet(null, envelope);
    }

    private void writeLoop() {
        try {
            while (running.get() || !queue.isEmpty()) {
                RawEnvelope record = queue.poll(100, TimeUnit.MILLISECONDS);
                if (record == null) {
                    continue;
                }
                write(record);
            }
            RawEnvelope first = firstDropped.get();
            if (first != null) {
                write(new RawEnvelope(
                        DataSourceModuleVersion.VERSION,
                        RawRecordType.RECOVERY,
                        first.generation(),
                        first.sourceId(),
                        first.exchange(),
                        first.symbol(),
                        System.currentTimeMillis(),
                        System.nanoTime(),
                        "",
                        "REPLAY_UNSAFE recorder dropped " + dropped.get()
                                + " records; firstDropEpochMillis=" + first.receivedEpochMillis()
                ));
            }
            writer.flush();
        } catch (Throwable error) {
            failure = error;
        }
    }

    private void write(RawEnvelope envelope) throws Exception {
        writer.write(mapper.writeValueAsString(envelope));
        writer.newLine();
        recorded.incrementAndGet();
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
        writer.close();
        if (failure != null) {
            throw new IllegalStateException("raw recorder failed", failure);
        }
    }
}

package com.example.hft.datasource.deepbook.runtime;

import com.example.hft.datasource.deepbook.DeepBookSourceDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;


public final class DeepBookReplayBenchmark {
    private static final int QUEUE_CAPACITY = 1_024;
    private static final long GENERATION_STRIDE = 1_000_000L;

    private final List<DeepBookSourceDefinition> sources;
    private final List<RawEnvelope> records;
    private final int snapshotLevels;
    private final ObjectMapper mapper;

    public DeepBookReplayBenchmark(
            List<DeepBookSourceDefinition> sources,
            List<RawEnvelope> records,
            int snapshotLevels,
            ObjectMapper mapper
    ) {
        if (records.isEmpty()) {
            throw new IllegalArgumentException("benchmark requires raw records");
        }
        this.sources = List.copyOf(sources);
        this.records = List.copyOf(records);
        this.snapshotLevels = snapshotLevels;
        this.mapper = mapper;
    }

    public BenchmarkRun runDirect(int cycles) {
        requirePositive(cycles, "cycles");
        IncrementalRawReplayProcessor processor = processor();
        SampleSet samples = new SampleSet(Math.multiplyExact(records.size(), cycles));
        long started = System.nanoTime();
        for (int cycle = 0; cycle < cycles; cycle++) {
            for (RawEnvelope record : records) {
                RawEnvelope replayRecord = forCycle(record, cycle);
                long processingStarted = System.nanoTime();
                processor.accept(replayRecord);
                long completed = System.nanoTime();
                samples.add(0L, completed - processingStarted, completed - processingStarted);
            }
        }
        long elapsed = System.nanoTime() - started;
        return new BenchmarkRun(
                result("direct", 1, samples, elapsed, 0L, 0L),
                processor.result()
        );
    }

    public BenchmarkRun runPartitioned(int cycles, int workerCount) throws InterruptedException {
        requirePositive(cycles, "cycles");
        requirePositive(workerCount, "workerCount");
        @SuppressWarnings("unchecked")
        ArrayBlockingQueue<Task>[] queues = new ArrayBlockingQueue[workerCount];
        Worker[] workers = new Worker[workerCount];
        Thread[] threads = new Thread[workerCount];
        CountDownLatch ready = new CountDownLatch(workerCount);
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        int expectedPerWorker = Math.max(16,
                Math.multiplyExact(records.size(), cycles) / workerCount);
        for (int i = 0; i < workerCount; i++) {
            queues[i] = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
            workers[i] = new Worker(queues[i], expectedPerWorker, ready, start, failure);
            threads[i] = new Thread(workers[i], "deep-book-replay-" + i);
            threads[i].start();
        }
        ready.await();
        long producerBlocked = 0L;
        long backpressureEvents = 0L;
        Map<String, Integer> partitions = balancedPartitions(workerCount);
        long started = System.nanoTime();
        start.countDown();
        for (int cycle = 0; cycle < cycles; cycle++) {
            for (RawEnvelope record : records) {
                RawEnvelope replayRecord = forCycle(record, cycle);
                int partition = partitions.get(record.sourceId());
                Task task = new Task(replayRecord, System.nanoTime(), false);
                if (!queues[partition].offer(task)) {
                    backpressureEvents++;
                    long blockedAt = System.nanoTime();
                    queues[partition].put(task);
                    producerBlocked += System.nanoTime() - blockedAt;
                }
            }
        }
        for (ArrayBlockingQueue<Task> queue : queues) {
            queue.put(Task.stopTask());
        }
        for (Thread thread : threads) {
            thread.join();
        }
        long elapsed = System.nanoTime() - started;
        Throwable workerFailure = failure.get();
        if (workerFailure != null) {
            throw new IllegalStateException("partitioned benchmark worker failed", workerFailure);
        }

        SampleSet combined = new SampleSet(Math.multiplyExact(records.size(), cycles));
        Map<String, LocalBookSnapshot> books = new LinkedHashMap<>();
        long applied = 0L;
        long ignored = 0L;
        for (Worker worker : workers) {
            combined.addAll(worker.samples);
            RawReplayResult workerResult = worker.processor.result();
            workerResult.finalBooks().forEach(books::put);
            applied += workerResult.appliedRecords();
            ignored += workerResult.ignoredRecords();
        }
        return new BenchmarkRun(
                result("partitioned", workerCount, combined, elapsed, producerBlocked, backpressureEvents),
                new RawReplayResult(books, applied, ignored)
        );
    }

    public static void requireSameBooks(RawReplayResult expected, RawReplayResult actual) {
        if (!expected.finalBooks().keySet().equals(actual.finalBooks().keySet())) {
            throw new IllegalStateException("benchmark source sets differ");
        }
        expected.finalBooks().forEach((sourceId, expectedBook) -> {
            LocalBookSnapshot actualBook = actual.finalBooks().get(sourceId);
            if (actualBook == null
                    || expectedBook.sequence() != actualBook.sequence()
                    || expectedBook.quality() != actualBook.quality()
                    || !expectedBook.bids().equals(actualBook.bids())
                    || !expectedBook.asks().equals(actualBook.asks())) {
                throw new IllegalStateException("benchmark book mismatch for " + sourceId);
            }
        });
    }

    private ReplayBenchmarkResult result(
            String mode,
            int workers,
            SampleSet samples,
            long elapsedNanos,
            long producerBlockedNanos,
            long backpressureEvents
    ) {
        long count = samples.size();
        ReplayBenchmarkResult result = new ReplayBenchmarkResult(
                mode,
                workers,
                count,
                elapsedNanos / 1_000_000.0,
                count * 1_000_000_000.0 / elapsedNanos,
                producerBlockedNanos / 1_000_000.0,
                backpressureEvents,
                samples.queue.distribution(),
                samples.processing.distribution(),
                samples.endToEnd.distribution()
        );
        return result;
    }

    private Map<String, Integer> balancedPartitions(int workerCount) {
        Map<String, Long> counts = new HashMap<>();
        for (RawEnvelope record : records) {
            counts.merge(record.sourceId(), 1L, Long::sum);
        }
        List<Map.Entry<String, Long>> busiestFirst = counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry.comparingByKey()))
                .toList();
        long[] assignedLoad = new long[workerCount];
        Map<String, Integer> result = new HashMap<>();
        for (Map.Entry<String, Long> entry : busiestFirst) {
            int target = 0;
            for (int candidate = 1; candidate < workerCount; candidate++) {
                if (assignedLoad[candidate] < assignedLoad[target]) {
                    target = candidate;
                }
            }
            result.put(entry.getKey(), target);
            assignedLoad[target] += entry.getValue();
        }
        return result;
    }
    private IncrementalRawReplayProcessor processor() {
        return new IncrementalRawReplayProcessor(sources, snapshotLevels, mapper);
    }

    private static RawEnvelope forCycle(RawEnvelope record, int cycle) {
        if (cycle == 0) {
            return record;
        }
        return new RawEnvelope(
                record.version(),
                record.recordType(),
                record.generation() + cycle * GENERATION_STRIDE,
                record.sourceId(),
                record.exchange(),
                record.symbol(),
                record.receivedEpochMillis(),
                record.receivedNanos(),
                record.payload(),
                record.detail()
        );
    }

    private static void requirePositive(int value, String label) {
        if (value <= 0) {
            throw new IllegalArgumentException(label + " must be positive");
        }
    }

    public record BenchmarkRun(ReplayBenchmarkResult metrics, RawReplayResult replay) {
    }


    private record Task(RawEnvelope record, long enqueuedNanos, boolean terminal) {
        private static Task stopTask() {
            return new Task(null, 0L, true);
        }
    }

    private final class Worker implements Runnable {
        private final ArrayBlockingQueue<Task> queue;
        private final SampleSet samples;
        private final CountDownLatch ready;
        private final CountDownLatch start;
        private final AtomicReference<Throwable> failure;
        private final IncrementalRawReplayProcessor processor = processor();

        private Worker(
                ArrayBlockingQueue<Task> queue,
                int expectedSamples,
                CountDownLatch ready,
                CountDownLatch start,
                AtomicReference<Throwable> failure
        ) {
            this.queue = queue;
            this.samples = new SampleSet(expectedSamples);
            this.ready = ready;
            this.start = start;
            this.failure = failure;
        }

        @Override
        public void run() {
            ready.countDown();
            try {
                start.await();
                while (true) {
                    Task task = queue.take();
                    if (task.terminal()) {
                        return;
                    }
                    long processingStarted = System.nanoTime();
                    processor.accept(task.record());
                    long completed = System.nanoTime();
                    samples.add(
                            processingStarted - task.enqueuedNanos(),
                            completed - processingStarted,
                            completed - task.enqueuedNanos()
                    );
                }
            } catch (Throwable error) {
                failure.compareAndSet(null, error);
            }
        }
    }

    private static final class SampleSet {
        private final LongSamples queue;
        private final LongSamples processing;
        private final LongSamples endToEnd;

        private SampleSet(int expected) {
            queue = new LongSamples(expected);
            processing = new LongSamples(expected);
            endToEnd = new LongSamples(expected);
        }

        private void add(long queueNanos, long processingNanos, long endToEndNanos) {
            queue.add(queueNanos);
            processing.add(processingNanos);
            endToEnd.add(endToEndNanos);
        }

        private void addAll(SampleSet other) {
            queue.addAll(other.queue);
            processing.addAll(other.processing);
            endToEnd.addAll(other.endToEnd);
        }

        private int size() {
            return processing.size;
        }
    }

    private static final class LongSamples {
        private long[] values;
        private int size;
        private long sum;
        private long max;

        private LongSamples(int expected) {
            values = new long[Math.max(16, expected)];
        }

        private void add(long value) {
            if (size == values.length) {
                values = Arrays.copyOf(values, Math.multiplyExact(values.length, 2));
            }
            values[size++] = value;
            sum += value;
            max = Math.max(max, value);
        }

        private void addAll(LongSamples other) {
            int required = size + other.size;
            if (required > values.length) {
                values = Arrays.copyOf(values, Math.max(required, values.length * 2));
            }
            System.arraycopy(other.values, 0, values, size, other.size);
            size = required;
            sum += other.sum;
            max = Math.max(max, other.max);
        }

        private ReplayBenchmarkResult.LatencyDistribution distribution() {
            if (size == 0) {
                return new ReplayBenchmarkResult.LatencyDistribution(0, 0, 0, 0, 0);
            }
            long[] sorted = Arrays.copyOf(values, size);
            Arrays.sort(sorted);
            return new ReplayBenchmarkResult.LatencyDistribution(
                    nanosToMicros((double) sum / size),
                    nanosToMicros(percentile(sorted, 0.50)),
                    nanosToMicros(percentile(sorted, 0.95)),
                    nanosToMicros(percentile(sorted, 0.99)),
                    nanosToMicros(max)
            );
        }

        private static long percentile(long[] sorted, double percentile) {
            int index = (int) Math.ceil(percentile * sorted.length) - 1;
            return sorted[Math.max(0, Math.min(index, sorted.length - 1))];
        }

        private static double nanosToMicros(double nanos) {
            return nanos / 1_000.0;
        }
    }
}
package com.example.hft.benchmark;

import java.util.Arrays;



public final class BenchmarkResult {
    private final String name;
    private final int workers;
    private final long count;
    private final long elapsedNanos;
    private final long producerWaitNanos;
    private final long queueWaitNanos;
    private final long processingNanos;
    private final long validationNanos;
    private final long decisionNanos;
    private final long analysisNanos;
    private final long buyPressure;
    private final long sellPressure;
    private final long neutral;
    private final long doNotTrade;
    private final long[] sortedE2eLatencies;

    private BenchmarkResult(String name, int workers, long count, long elapsedNanos, long producerWaitNanos,
                            WorkerMetrics[] metrics) {
        this.name = name;
        this.workers = workers;
        this.count = count;
        this.elapsedNanos = elapsedNanos;
        this.producerWaitNanos = producerWaitNanos;

        ModuleTiming totalTiming = new ModuleTiming();
        long queueWait = 0;
        long processing = 0;
        long buy = 0;
        long sell = 0;
        long flat = 0;
        long noTrade = 0;
        long[] latencies = new long[(int) count];
        int offset = 0;

        for (WorkerMetrics metric : metrics) {
            totalTiming.merge(metric.moduleTiming());
            queueWait += metric.queueWaitNanos();
            processing += metric.processingNanos();
            buy += metric.buyPressure();
            sell += metric.sellPressure();
            flat += metric.neutral();
            noTrade += metric.doNotTrade();
            System.arraycopy(metric.e2eLatencies(), 0, latencies, offset, (int) metric.processed());
            offset += (int) metric.processed();
        }

        Arrays.sort(latencies);
        this.sortedE2eLatencies = latencies;
        this.queueWaitNanos = queueWait;
        this.processingNanos = processing;
        this.validationNanos = totalTiming.validationNanos();
        this.decisionNanos = totalTiming.decisionNanos();
        this.analysisNanos = totalTiming.analysisNanos();
        this.buyPressure = buy;
        this.sellPressure = sell;
        this.neutral = flat;
        this.doNotTrade = noTrade;
    }

    public static BenchmarkResult of(String name, int workers, long count, long elapsedNanos, long producerWaitNanos,
                                     WorkerMetrics[] metrics) {
        return new BenchmarkResult(name, workers, count, elapsedNanos, producerWaitNanos, metrics);
    }

    public String name() {
        return name;
    }

    public double avgValidationNanos() {
        return perMessage(validationNanos);
    }

    public double avgDecisionNanos() {
        return perMessage(decisionNanos);
    }

    public double avgQueueWaitNanos() {
        return perMessage(queueWaitNanos);
    }

    public double avgProcessingNanos() {
        return perMessage(processingNanos);
    }

    public String toDisplayLine() {
        return String.format(
                "%-19s workers=%d count=%d elapsed=%7.2f ms throughput=%10.0f msg/s avgE2E=%7.2f us p99E2E=%7.2f us avgQueue=%7.2f us avgProcessor=%7.2f us validate=%6.1f ns decision=%6.1f ns producerWait=%7.2f ms signals[B=%d S=%d N=%d X=%d]",
                name,
                workers,
                count,
                nanosToMillis(elapsedNanos),
                count / (elapsedNanos / 1_000_000_000.0),
                nanosToMicros(averageE2eNanos()),
                nanosToMicros(percentile(0.99)),
                nanosToMicros(avgQueueWaitNanos()),
                nanosToMicros(avgProcessingNanos()),
                avgValidationNanos(),
                avgDecisionNanos(),
                nanosToMillis(producerWaitNanos),
                buyPressure,
                sellPressure,
                neutral,
                doNotTrade
        );
    }

    public String deltaLine(BenchmarkResult baseline) {
        return String.format(
                "%-19s delta vs %-10s queue=%+7.1f ns processor=%+7.1f ns validation=%+7.1f ns decision=%+7.1f ns",
                name,
                baseline.name,
                avgQueueWaitNanos() - baseline.avgQueueWaitNanos(),
                avgProcessingNanos() - baseline.avgProcessingNanos(),
                avgValidationNanos() - baseline.avgValidationNanos(),
                avgDecisionNanos() - baseline.avgDecisionNanos()
        );
    }

    private double averageE2eNanos() {
        long total = 0;
        for (long latency : sortedE2eLatencies) {
            total += latency;
        }
        return perMessage(total);
    }

    private long percentile(double percentile) {
        int index = (int) Math.ceil(sortedE2eLatencies.length * percentile) - 1;
        return sortedE2eLatencies[Math.max(0, Math.min(index, sortedE2eLatencies.length - 1))];
    }

    private double perMessage(long nanos) {
        return count == 0 ? 0.0 : (double) nanos / count;
    }

    private static double nanosToMillis(long nanos) {
        return nanos / 1_000_000.0;
    }

    private static double nanosToMicros(double nanos) {
        return nanos / 1_000.0;
    }
}

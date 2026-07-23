package com.example.hft.datasource.deepbook.runtime;


public record ReplayBenchmarkResult(
        String mode,
        int workers,
        long records,
        double elapsedMillis,
        double throughputPerSecond,
        double producerBlockedMillis,
        long backpressureEvents,
        LatencyDistribution queueLatency,
        LatencyDistribution processingLatency,
        LatencyDistribution endToEndLatency
) {
    public record LatencyDistribution(
            double averageMicros,
            double p50Micros,
            double p95Micros,
            double p99Micros,
            double maxMicros
    ) {
    }
}
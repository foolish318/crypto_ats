package com.example.hft.datasource.deepbook.runtime;


public record PipelineLatencyDistribution(
        long samples,
        double averageMicros,
        double p50Micros,
        double p95Micros,
        double p99Micros,
        double p999Micros,
        double maxMicros
) {
}

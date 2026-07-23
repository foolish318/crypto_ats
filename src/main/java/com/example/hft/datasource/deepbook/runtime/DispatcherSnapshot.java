package com.example.hft.datasource.deepbook.runtime;


public record DispatcherSnapshot(
        int workers,
        int queueCapacityPerWorker,
        long submitted,
        long completed,
        long queueFullRejections,
        long taskFailures,
        long maxQueueDepth,
        double averageQueueWaitMicros,
        double maxQueueWaitMicros,
        double averageProcessingMicros,
        double maxProcessingMicros
) {
}
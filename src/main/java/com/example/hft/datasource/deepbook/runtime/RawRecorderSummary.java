package com.example.hft.datasource.deepbook.runtime;


public record RawRecorderSummary(
        long recordedRecords,
        long droppedRecords,
        boolean replaySafe,
        long firstDropEpochMillis,
        String firstDropReason,
        String failure,
        int queueCapacity,
        int queueDepth,
        int maxQueueDepth,
        long lastWriteLagNanos,
        long maxWriteLagNanos,
        String currentSegment,
        long diskUsageBytes
) {
    public RawRecorderSummary(
            long recordedRecords,
            long droppedRecords,
            boolean replaySafe,
            long firstDropEpochMillis,
            String firstDropReason,
            String failure
    ) {
        this(
                recordedRecords, droppedRecords, replaySafe, firstDropEpochMillis,
                firstDropReason, failure, 0, 0, 0, 0L, 0L, "", 0L
        );
    }
}

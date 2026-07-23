package com.example.hft.datasource.deepbook.runtime;


public record RawRecorderSummary(
        long recordedRecords,
        long droppedRecords,
        boolean replaySafe,
        long firstDropEpochMillis,
        String firstDropReason,
        String failure
) {
}

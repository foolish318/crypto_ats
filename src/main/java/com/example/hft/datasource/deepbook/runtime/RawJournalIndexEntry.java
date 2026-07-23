package com.example.hft.datasource.deepbook.runtime;


public record RawJournalIndexEntry(
        int segmentIndex,
        String file,
        long firstFrameIndex,
        long lastFrameIndex,
        long records,
        long bytes,
        long openedEpochMillis,
        long closedEpochMillis
) {
}

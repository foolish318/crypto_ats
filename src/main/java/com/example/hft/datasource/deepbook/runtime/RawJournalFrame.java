package com.example.hft.datasource.deepbook.runtime;


public record RawJournalFrame(
        RawJournalFrameType frameType,
        String journalVersion,
        int segmentIndex,
        long frameIndex,
        long createdEpochMillis,
        String sourceMetadata,
        String checksum,
        RawEnvelope record
) {
    public RawJournalFrame {
        if (frameType == null || journalVersion == null || journalVersion.isBlank()
                || segmentIndex < 0 || frameIndex < 0L || checksum == null) {
            throw new IllegalArgumentException("invalid raw journal frame");
        }
        sourceMetadata = sourceMetadata == null ? "" : sourceMetadata;
    }
}

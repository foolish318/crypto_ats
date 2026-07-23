package com.example.hft.datasource.deepbook.runtime;

import java.time.Duration;


public record RawJournalConfig(
        long maxSegmentBytes,
        Duration maxSegmentDuration,
        Duration retention,
        long minimumFreeDiskBytes,
        int flushEveryRecords,
        int fsyncEveryRecords,
        String sourceMetadata
) {
    public RawJournalConfig {
        if (maxSegmentBytes <= 0L
                || maxSegmentDuration == null
                || maxSegmentDuration.isNegative()
                || maxSegmentDuration.isZero()
                || retention == null
                || retention.isNegative()
                || retention.isZero()
                || minimumFreeDiskBytes < 0L
                || flushEveryRecords <= 0
                || fsyncEveryRecords <= 0
                || sourceMetadata == null) {
            throw new IllegalArgumentException("invalid raw journal configuration");
        }
    }

    public static RawJournalConfig defaults() {
        return new RawJournalConfig(
                128L * 1024L * 1024L,
                Duration.ofMinutes(15),
                Duration.ofHours(24),
                64L * 1024L * 1024L,
                256,
                4_096,
                "multi-source; record identity is authoritative"
        );
    }
}

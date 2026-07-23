package com.example.hft.datasource.deepbook.runtime;


public record RawReplayCursor(
        int segmentIndex,
        long frameIndex,
        long appliedRecords,
        long ignoredRecords
) {
}

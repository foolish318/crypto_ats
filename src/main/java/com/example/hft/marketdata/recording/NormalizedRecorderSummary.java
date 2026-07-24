package com.example.hft.marketdata.recording;

public record NormalizedRecorderSummary(
        long recorded,
        long dropped,
        boolean replaySafe,
        int queueDepth,
        int maxQueueDepth,
        long lastWriteLagNanos,
        long maxWriteLagNanos,
        String failure
) {
}
package com.example.hft.datasource.engine;


public record AsyncListenerSnapshot(
        String name,
        int capacity,
        int queueDepth,
        int maxQueueDepth,
        long acceptedEvents,
        long droppedEvents,
        long errors,
        long lastLagNanos,
        long maxLagNanos,
        String lastError
) {
    public boolean healthy() {
        return droppedEvents == 0L && errors == 0L;
    }
}

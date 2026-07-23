package com.example.hft.datasource.engine;


public record EnginePublishResult(
        boolean published,
        long cacheNanos,
        long coreListenerNanos,
        long asyncOfferNanos
) {
    public static EnginePublishResult rejected(long cacheNanos) {
        return new EnginePublishResult(false, cacheNanos, 0L, 0L);
    }
}

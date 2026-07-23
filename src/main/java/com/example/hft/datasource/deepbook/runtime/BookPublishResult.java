package com.example.hft.datasource.deepbook.runtime;


public record BookPublishResult(
        boolean published,
        long snapshotNanos,
        long cacheNanos,
        long coreListenerNanos,
        long asyncOfferNanos
) {
    public static BookPublishResult notPublished() {
        return new BookPublishResult(false, 0L, 0L, 0L, 0L);
    }
}

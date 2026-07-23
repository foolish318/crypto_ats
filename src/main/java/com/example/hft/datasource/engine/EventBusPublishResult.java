package com.example.hft.datasource.engine;


public record EventBusPublishResult(
        long coreListenerNanos,
        long asyncOfferNanos
) {
    public EventBusPublishResult plus(EventBusPublishResult other) {
        return new EventBusPublishResult(
                coreListenerNanos + other.coreListenerNanos,
                asyncOfferNanos + other.asyncOfferNanos
        );
    }
}

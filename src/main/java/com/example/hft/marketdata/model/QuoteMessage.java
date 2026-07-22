package com.example.hft.marketdata.model;


public record QuoteMessage(Quote quote, long enqueuedNanos) implements QuoteEvent {
    @Override
    public boolean isStop() {
        return false;
    }
}

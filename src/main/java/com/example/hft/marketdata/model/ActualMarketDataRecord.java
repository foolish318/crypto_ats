package com.example.hft.marketdata.model;


public final class ActualMarketDataRecord {
    private final Quote quote;
    private final long exchangeEventTimeMillis;
    private final long localReceivedEpochMillis;
    private final long rawReceivedNanos;
    private final long parsedNanos;
    private final String rawPayload;

    public ActualMarketDataRecord(Quote quote, long exchangeEventTimeMillis, long localReceivedEpochMillis,
                                  long rawReceivedNanos, long parsedNanos, String rawPayload) {
        this.quote = quote;
        this.exchangeEventTimeMillis = exchangeEventTimeMillis;
        this.localReceivedEpochMillis = localReceivedEpochMillis;
        this.rawReceivedNanos = rawReceivedNanos;
        this.parsedNanos = parsedNanos;
        this.rawPayload = rawPayload;
    }

    public Quote quote() {
        return quote;
    }

    public long exchangeEventTimeMillis() {
        return exchangeEventTimeMillis;
    }

    public long localReceivedEpochMillis() {
        return localReceivedEpochMillis;
    }

    public long rawReceivedNanos() {
        return rawReceivedNanos;
    }

    public long parsedNanos() {
        return parsedNanos;
    }

    public String rawPayload() {
        return rawPayload;
    }
}

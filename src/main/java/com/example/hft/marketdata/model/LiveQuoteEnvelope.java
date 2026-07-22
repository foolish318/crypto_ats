package com.example.hft.marketdata.model;


public final class LiveQuoteEnvelope {
    private static final LiveQuoteEnvelope STOP = new LiveQuoteEnvelope(null, 0, 0, 0, true);

    private final Quote quote;
    private final long rawReceivedNanos;
    private final long parsedNanos;
    private final long enqueuedNanos;
    private final boolean stop;

    private LiveQuoteEnvelope(Quote quote, long rawReceivedNanos, long parsedNanos, long enqueuedNanos, boolean stop) {
        this.quote = quote;
        this.rawReceivedNanos = rawReceivedNanos;
        this.parsedNanos = parsedNanos;
        this.enqueuedNanos = enqueuedNanos;
        this.stop = stop;
    }

    public static LiveQuoteEnvelope quote(Quote quote, long rawReceivedNanos, long parsedNanos, long enqueuedNanos) {
        return new LiveQuoteEnvelope(quote, rawReceivedNanos, parsedNanos, enqueuedNanos, false);
    }

    public static LiveQuoteEnvelope stop() {
        return STOP;
    }

    public Quote quote() {
        return quote;
    }

    public long rawReceivedNanos() {
        return rawReceivedNanos;
    }

    public long parsedNanos() {
        return parsedNanos;
    }

    public long enqueuedNanos() {
        return enqueuedNanos;
    }

    public boolean isStop() {
        return stop;
    }
}

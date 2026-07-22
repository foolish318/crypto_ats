package com.example.hft.marketdata.model;


public final class ActualLatencyEnvelope {
    private static final ActualLatencyEnvelope STOP = new ActualLatencyEnvelope(null, 0, true);

    private final ActualMarketDataRecord record;
    private final long enqueuedNanos;
    private final boolean stop;

    private ActualLatencyEnvelope(ActualMarketDataRecord record, long enqueuedNanos, boolean stop) {
        this.record = record;
        this.enqueuedNanos = enqueuedNanos;
        this.stop = stop;
    }

    public static ActualLatencyEnvelope record(ActualMarketDataRecord record, long enqueuedNanos) {
        return new ActualLatencyEnvelope(record, enqueuedNanos, false);
    }

    public static ActualLatencyEnvelope stop() {
        return STOP;
    }

    public ActualMarketDataRecord record() {
        return record;
    }

    public long enqueuedNanos() {
        return enqueuedNanos;
    }

    public boolean isStop() {
        return stop;
    }
}

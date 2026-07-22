package com.example.hft.marketdata.model;


public final class DepthLatencyEnvelope {
    private static final DepthLatencyEnvelope STOP = new DepthLatencyEnvelope(null, 0, true);

    private final DepthMarketDataRecord record;
    private final long enqueuedNanos;
    private final boolean stop;

    private DepthLatencyEnvelope(DepthMarketDataRecord record, long enqueuedNanos, boolean stop) {
        this.record = record;
        this.enqueuedNanos = enqueuedNanos;
        this.stop = stop;
    }

    public static DepthLatencyEnvelope record(DepthMarketDataRecord record, long enqueuedNanos) {
        return new DepthLatencyEnvelope(record, enqueuedNanos, false);
    }

    public static DepthLatencyEnvelope stop() {
        return STOP;
    }

    public DepthMarketDataRecord record() {
        return record;
    }

    public long enqueuedNanos() {
        return enqueuedNanos;
    }

    public boolean isStop() {
        return stop;
    }
}

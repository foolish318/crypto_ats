package com.example.hft.pipeline;

import com.example.hft.marketdata.model.DepthMarketDataRecord;


public final class DisruptorDepthEvent {
    private DepthMarketDataRecord record;
    private long enqueuedNanos;
    private boolean stop;

    public void set(DepthMarketDataRecord record, long enqueuedNanos, boolean stop) {
        this.record = record;
        this.enqueuedNanos = enqueuedNanos;
        this.stop = stop;
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

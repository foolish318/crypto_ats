package com.example.hft.benchmark;

import com.example.hft.marketdata.model.TradingSignal;


public final class WorkerMetrics {
    private final ModuleTiming moduleTiming = new ModuleTiming();
    private final long[] e2eLatencies;

    private long processed;
    private long buyPressure;
    private long sellPressure;
    private long neutral;
    private long doNotTrade;
    private long queueWaitNanos;
    private long processingNanos;
    private long e2eNanos;

    public WorkerMetrics(int capacity) {
        this.e2eLatencies = new long[capacity];
    }

    public void record(TradingSignal signal, long queueWaitNanos, long processingNanos, long e2eNanos) {
        e2eLatencies[(int) processed] = e2eNanos;
        processed++;
        this.queueWaitNanos += queueWaitNanos;
        this.processingNanos += processingNanos;
        this.e2eNanos += e2eNanos;

        switch (signal) {
            case BUY_PRESSURE -> buyPressure++;
            case SELL_PRESSURE -> sellPressure++;
            case NEUTRAL -> neutral++;
            case DO_NOT_TRADE -> doNotTrade++;
        }
    }

    public ModuleTiming moduleTiming() {
        return moduleTiming;
    }

    public long processed() {
        return processed;
    }

    public long buyPressure() {
        return buyPressure;
    }

    public long sellPressure() {
        return sellPressure;
    }

    public long neutral() {
        return neutral;
    }

    public long doNotTrade() {
        return doNotTrade;
    }

    public long queueWaitNanos() {
        return queueWaitNanos;
    }

    public long processingNanos() {
        return processingNanos;
    }

    public long e2eNanos() {
        return e2eNanos;
    }

    public long[] e2eLatencies() {
        return e2eLatencies;
    }
}

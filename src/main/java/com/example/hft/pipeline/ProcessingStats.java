package com.example.hft.pipeline;

import com.example.hft.marketdata.model.TradingSignal;
import java.util.concurrent.atomic.AtomicInteger;



public final class ProcessingStats {
    private final AtomicInteger processed = new AtomicInteger();
    private final AtomicInteger buyPressure = new AtomicInteger();
    private final AtomicInteger sellPressure = new AtomicInteger();
    private final AtomicInteger neutral = new AtomicInteger();
    private final AtomicInteger doNotTrade = new AtomicInteger();

    public void record(TradingSignal signal) {
        processed.incrementAndGet();
        switch (signal) {
            case BUY_PRESSURE -> buyPressure.incrementAndGet();
            case SELL_PRESSURE -> sellPressure.incrementAndGet();
            case NEUTRAL -> neutral.incrementAndGet();
            case DO_NOT_TRADE -> doNotTrade.incrementAndGet();
        }
    }

    public int processed() {
        return processed.get();
    }

    public String toDisplayLine() {
        return "stats processed=" + processed.get()
                + " buyPressure=" + buyPressure.get()
                + " sellPressure=" + sellPressure.get()
                + " neutral=" + neutral.get()
                + " doNotTrade=" + doNotTrade.get();
    }
}

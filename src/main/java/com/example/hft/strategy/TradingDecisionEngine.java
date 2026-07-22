package com.example.hft.strategy;

import com.example.hft.marketdata.model.Quote;
import com.example.hft.marketdata.model.TradingSignal;


public final class TradingDecisionEngine {
    private static final long WIDE_SPREAD_THRESHOLD_TICKS = 10;
    private static final double LARGE_IMBALANCE_RATIO = 2.0;

    public TradingSignal evaluate(Quote quote) {
        long spreadTicks = quote.spreadTicks();
        if (spreadTicks > WIDE_SPREAD_THRESHOLD_TICKS) {
            return TradingSignal.DO_NOT_TRADE;
        }

        double imbalance = (double) quote.bidSize() / quote.askSize();
        if (imbalance >= LARGE_IMBALANCE_RATIO) {
            return TradingSignal.BUY_PRESSURE;
        }
        if (imbalance <= 1.0 / LARGE_IMBALANCE_RATIO) {
            return TradingSignal.SELL_PRESSURE;
        }
        return TradingSignal.NEUTRAL;
    }
}

package com.example.hft.strategy;

import com.example.hft.marketdata.model.DepthMarketDataRecord;
import com.example.hft.marketdata.model.TradingSignal;


public final class DepthDecisionEngine {
    private static final long MAX_TOP_SPREAD_TICKS = 10;
    private static final double LEVEL5_IMBALANCE = 1.50;
    private static final double LEVEL10_IMBALANCE = 1.20;

    public TradingSignal evaluate(DepthMarketDataRecord record) {
        if (record.levels() < 10 || record.bidPrice(0) <= 0 || record.askPrice(0) <= 0) {
            return TradingSignal.DO_NOT_TRADE;
        }

        long topSpread = record.topSpreadTicks();
        if (topSpread <= 0 || topSpread > MAX_TOP_SPREAD_TICKS) {
            return TradingSignal.DO_NOT_TRADE;
        }

        double imbalance5 = ratio(record.bidVolume(5), record.askVolume(5));
        double imbalance10 = ratio(record.bidVolume(10), record.askVolume(10));
        long weightedSpread5 = record.weightedAskTicks(5) - record.weightedBidTicks(5);
        long weightedSpread10 = record.weightedAskTicks(10) - record.weightedBidTicks(10);

        if (weightedSpread5 <= 0 || weightedSpread10 <= 0) {
            return TradingSignal.DO_NOT_TRADE;
        }
        if (imbalance5 >= LEVEL5_IMBALANCE && imbalance10 >= LEVEL10_IMBALANCE) {
            return TradingSignal.BUY_PRESSURE;
        }
        if (imbalance5 <= 1.0 / LEVEL5_IMBALANCE && imbalance10 <= 1.0 / LEVEL10_IMBALANCE) {
            return TradingSignal.SELL_PRESSURE;
        }
        return TradingSignal.NEUTRAL;
    }

    private static double ratio(long numerator, long denominator) {
        return denominator == 0 ? Double.POSITIVE_INFINITY : (double) numerator / denominator;
    }
}

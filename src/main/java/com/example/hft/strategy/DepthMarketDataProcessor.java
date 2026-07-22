package com.example.hft.strategy;

import com.example.hft.marketdata.model.DepthMarketDataRecord;
import com.example.hft.marketdata.model.TradingSignal;


public final class DepthMarketDataProcessor {
    private final DepthDecisionEngine decisionEngine = new DepthDecisionEngine();

    public TradingSignal signalFor(DepthMarketDataRecord record) {
        return decisionEngine.evaluate(record);
    }
}

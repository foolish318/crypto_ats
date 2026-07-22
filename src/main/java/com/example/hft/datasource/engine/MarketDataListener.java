package com.example.hft.datasource.engine;

import com.example.hft.datasource.normalizer.NormalizedMarketDataEvent;


public interface MarketDataListener {
    void onMarketData(NormalizedMarketDataEvent event);
}

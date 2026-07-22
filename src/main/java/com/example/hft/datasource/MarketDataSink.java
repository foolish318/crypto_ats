package com.example.hft.datasource;

import com.example.hft.datasource.normalizer.NormalizedMarketDataEvent;


public interface MarketDataSink {
    void onEvent(NormalizedMarketDataEvent event);

    default void onHealth(DataSourceHealth health) {
    }

    default void onError(String source, Throwable error) {
    }
}

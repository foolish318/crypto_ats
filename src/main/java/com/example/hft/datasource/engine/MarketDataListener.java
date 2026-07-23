package com.example.hft.datasource.engine;

import com.example.hft.datasource.deepbook.runtime.BookAvailabilityEvent;
import com.example.hft.datasource.normalizer.NormalizedMarketDataEvent;


public interface MarketDataListener {
    void onMarketData(NormalizedMarketDataEvent event);

    default void onBookAvailability(BookAvailabilityEvent event) {
    }
}

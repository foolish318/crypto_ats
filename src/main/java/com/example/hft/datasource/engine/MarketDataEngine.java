package com.example.hft.datasource.engine;

import com.example.hft.datasource.DataSourceHealth;
import com.example.hft.datasource.MarketDataSink;
import com.example.hft.datasource.normalizer.NormalizedMarketDataEvent;


public final class MarketDataEngine implements MarketDataSink {
    private final MarketDataCache cache;
    private final MarketDataEventBus eventBus;

    public MarketDataEngine(MarketDataCache cache, MarketDataEventBus eventBus) {
        this.cache = cache;
        this.eventBus = eventBus;
    }

    @Override
    public void onEvent(NormalizedMarketDataEvent event) {
        cache.update(event);
        eventBus.publish(event);
    }

    @Override
    public void onHealth(DataSourceHealth health) {
        // Health events will later feed monitoring and reconnect policy.
    }

    @Override
    public void onError(String source, Throwable error) {
        // Error handling will later feed reconnect and degradation policy.
    }

    public MarketDataCache cache() {
        return cache;
    }
}

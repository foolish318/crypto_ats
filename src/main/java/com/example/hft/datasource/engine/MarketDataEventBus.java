package com.example.hft.datasource.engine;

import com.example.hft.datasource.normalizer.NormalizedMarketDataEvent;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;


public final class MarketDataEventBus {
    private final List<MarketDataListener> listeners = new CopyOnWriteArrayList<>();

    public void subscribe(MarketDataListener listener) {
        listeners.add(listener);
    }

    public void publish(NormalizedMarketDataEvent event) {
        for (MarketDataListener listener : listeners) {
            listener.onMarketData(event);
        }
    }

    public int listenerCount() {
        return listeners.size();
    }
}

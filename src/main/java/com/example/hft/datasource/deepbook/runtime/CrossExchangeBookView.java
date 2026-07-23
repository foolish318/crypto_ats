package com.example.hft.datasource.deepbook.runtime;

import com.example.hft.datasource.engine.MarketDataListener;
import com.example.hft.datasource.normalizer.NormalizedMarketDataEvent;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


public final class CrossExchangeBookView implements MarketDataListener {
    private final Map<String, AcceptedLocalBookEvent> latestByVenueSymbol = new ConcurrentHashMap<>();

    @Override
    public void onMarketData(NormalizedMarketDataEvent event) {
        if (event instanceof AcceptedLocalBookEvent accepted) {
            latestByVenueSymbol.put(key(accepted.exchange(), accepted.symbol()), accepted);
        }
    }

    public Collection<AcceptedLocalBookEvent> books() {
        return List.copyOf(latestByVenueSymbol.values());
    }

    public int size() {
        return latestByVenueSymbol.size();
    }

    private static String key(String exchange, String symbol) {
        return exchange + "|" + symbol;
    }

}

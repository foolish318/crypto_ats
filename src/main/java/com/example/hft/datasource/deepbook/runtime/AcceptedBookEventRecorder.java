package com.example.hft.datasource.deepbook.runtime;

import com.example.hft.datasource.engine.MarketDataListener;
import com.example.hft.datasource.normalizer.NormalizedMarketDataEvent;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;


public final class AcceptedBookEventRecorder implements MarketDataListener {
    private final AtomicLong recorded = new AtomicLong();
    private final Map<String, AcceptedLocalBookEvent> latestByVenueSymbol = new ConcurrentHashMap<>();

    @Override
    public void onMarketData(NormalizedMarketDataEvent event) {
        if (event instanceof AcceptedLocalBookEvent accepted) {
            latestByVenueSymbol.put(key(accepted.exchange(), accepted.symbol()), accepted);
            recorded.incrementAndGet();
        }
    }

    public long recorded() {
        return recorded.get();
    }

    public Optional<AcceptedLocalBookEvent> latest(String exchange, String symbol) {
        return Optional.ofNullable(latestByVenueSymbol.get(key(exchange, symbol)));
    }

    private static String key(String exchange, String symbol) {
        return exchange + "|" + symbol;
    }
}

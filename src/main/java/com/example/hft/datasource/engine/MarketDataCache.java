package com.example.hft.datasource.engine;

import com.example.hft.datasource.deepbook.runtime.AcceptedLocalBookEvent;
import com.example.hft.datasource.normalizer.NormalizedMarketDataEvent;
import com.example.hft.datasource.normalizer.TopOfBookEvent;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;


public final class MarketDataCache {
    private final Map<String, TopOfBookEvent> topOfBookByVenueSymbol = new ConcurrentHashMap<>();
    private final Map<String, AcceptedLocalBookEvent> deepBookByVenueSymbol = new ConcurrentHashMap<>();

    public void update(NormalizedMarketDataEvent event) {
        if (event instanceof TopOfBookEvent topOfBook) {
            topOfBookByVenueSymbol.put(key(topOfBook.exchange(), topOfBook.symbol()), topOfBook);
        } else if (event instanceof AcceptedLocalBookEvent deepBook) {
            deepBookByVenueSymbol.put(key(deepBook.exchange(), deepBook.symbol()), deepBook);
        }
    }

    public Optional<TopOfBookEvent> topOfBook(String exchange, String symbol) {
        return Optional.ofNullable(topOfBookByVenueSymbol.get(key(exchange, symbol)));
    }

    public Optional<AcceptedLocalBookEvent> deepBook(String exchange, String symbol) {
        return Optional.ofNullable(deepBookByVenueSymbol.get(key(exchange, symbol)));
    }

    public int topOfBookCount() {
        return topOfBookByVenueSymbol.size();
    }

    public int deepBookCount() {
        return deepBookByVenueSymbol.size();
    }

    private static String key(String exchange, String symbol) {
        return exchange + "|" + symbol;
    }
}

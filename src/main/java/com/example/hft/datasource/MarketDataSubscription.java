package com.example.hft.datasource;

import java.util.Set;


public record MarketDataSubscription(
        String exchange,
        String symbol,
        Set<MarketDataChannel> channels,
        int depth
) {
    public MarketDataSubscription {
        channels = Set.copyOf(channels);
    }

    public static MarketDataSubscription topOfBook(String exchange, String symbol) {
        return new MarketDataSubscription(exchange, symbol, Set.of(MarketDataChannel.TOP_OF_BOOK), 1);
    }
}

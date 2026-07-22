package com.example.hft.datasource.normalizer;

import com.example.hft.datasource.transport.TransportType;
import com.example.hft.marketdata.model.OrderBookLevel;
import java.util.List;


public record BookDepthEvent(
        String source,
        String exchange,
        String symbol,
        TransportType transport,
        long receivedNanos,
        long exchangeTimeMillis,
        long sequence,
        BookEventType eventType,
        List<OrderBookLevel> bids,
        List<OrderBookLevel> asks
) implements NormalizedMarketDataEvent {
    public BookDepthEvent {
        bids = List.copyOf(bids);
        asks = List.copyOf(asks);
    }
}

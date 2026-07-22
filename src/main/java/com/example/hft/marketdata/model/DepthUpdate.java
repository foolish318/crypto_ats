package com.example.hft.marketdata.model;

import java.util.List;



public final class DepthUpdate {
    private final String symbol;
    private final long exchangeEventTimeMillis;
    private final long firstUpdateId;
    private final long finalUpdateId;
    private final List<OrderBookLevel> bids;
    private final List<OrderBookLevel> asks;

    public DepthUpdate(String symbol, long exchangeEventTimeMillis, long firstUpdateId, long finalUpdateId,
                       List<OrderBookLevel> bids, List<OrderBookLevel> asks) {
        this.symbol = symbol;
        this.exchangeEventTimeMillis = exchangeEventTimeMillis;
        this.firstUpdateId = firstUpdateId;
        this.finalUpdateId = finalUpdateId;
        this.bids = List.copyOf(bids);
        this.asks = List.copyOf(asks);
    }

    public String symbol() {
        return symbol;
    }

    public long exchangeEventTimeMillis() {
        return exchangeEventTimeMillis;
    }

    public long firstUpdateId() {
        return firstUpdateId;
    }

    public long finalUpdateId() {
        return finalUpdateId;
    }

    public List<OrderBookLevel> bids() {
        return bids;
    }

    public List<OrderBookLevel> asks() {
        return asks;
    }
}

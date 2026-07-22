package com.example.hft.marketdata.model;


public final class OrderBookLevel {
    private final long priceTicks;
    private final int size;

    public OrderBookLevel(long priceTicks, int size) {
        this.priceTicks = priceTicks;
        this.size = size;
    }

    public long priceTicks() {
        return priceTicks;
    }

    public int size() {
        return size;
    }
}

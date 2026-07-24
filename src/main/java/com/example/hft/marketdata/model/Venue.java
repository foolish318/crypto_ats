package com.example.hft.marketdata.model;

public enum Venue {
    BINANCE_US,
    OKX,
    KRAKEN;

    public static Venue fromExchange(String exchange) {
        if (exchange == null || exchange.isBlank()) {
            throw new IllegalArgumentException("exchange is required");
        }
        return Venue.valueOf(exchange);
    }
}
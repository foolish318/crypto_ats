package com.example.hft.datasource.instrument;

import java.math.BigDecimal;


public record Instrument(
        String exchange,
        String exchangeSymbol,
        String canonicalSymbol,
        String baseAsset,
        String quoteAsset,
        BigDecimal tickSize,
        BigDecimal lotSize
) {
    public Instrument {
        if (exchange == null || exchange.isBlank()
                || exchangeSymbol == null || exchangeSymbol.isBlank()
                || canonicalSymbol == null || canonicalSymbol.isBlank()
                || baseAsset == null || baseAsset.isBlank()
                || quoteAsset == null || quoteAsset.isBlank()) {
            throw new IllegalArgumentException("instrument identity fields are required");
        }
        if (tickSize == null || tickSize.signum() <= 0
                || lotSize == null || lotSize.signum() <= 0) {
            throw new IllegalArgumentException("tickSize and lotSize must be positive");
        }
    }
}
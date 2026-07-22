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
}

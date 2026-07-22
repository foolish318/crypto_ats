package com.example.hft.strategy;

import com.example.hft.marketdata.model.Quote;


public final class QuoteValidator {
    public void validate(Quote quote) {
        if (quote.symbol().isBlank()) {
            throw new IllegalArgumentException("symbol must not be blank");
        }
        if (quote.bidSize() <= 0) {
            throw new IllegalArgumentException("bid size must be positive");
        }
        if (quote.askSize() <= 0) {
            throw new IllegalArgumentException("ask size must be positive");
        }
        if (quote.bidPrice().compareTo(quote.askPrice()) >= 0) {
            throw new IllegalArgumentException("bid price must be lower than ask price");
        }
    }
}

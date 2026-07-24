package com.example.hft.marketdata.model;

import java.math.BigDecimal;

public record BookLevel(BigDecimal price, BigDecimal quantity) {
    public BookLevel {
        if (price == null || price.signum() <= 0) {
            throw new IllegalArgumentException("book price must be positive");
        }
        if (quantity == null || quantity.signum() < 0) {
            throw new IllegalArgumentException("book quantity must be non-negative");
        }
    }
}
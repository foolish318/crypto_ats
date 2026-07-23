package com.example.hft.datasource.deepbook.runtime;

import java.math.BigDecimal;


public record DecimalBookLevel(BigDecimal price, BigDecimal quantity) {
    public DecimalBookLevel {
        if (price == null || price.signum() <= 0) {
            throw new IllegalArgumentException("price must be positive");
        }
        if (quantity == null || quantity.signum() < 0) {
            throw new IllegalArgumentException("quantity must be non-negative");
        }
    }
}

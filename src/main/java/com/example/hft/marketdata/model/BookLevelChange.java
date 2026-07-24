package com.example.hft.marketdata.model;

import java.math.BigDecimal;

public record BookLevelChange(Side side, BigDecimal price, BigDecimal quantity) {
    public BookLevelChange {
        if (side == null || price == null || price.signum() <= 0
                || quantity == null || quantity.signum() < 0) {
            throw new IllegalArgumentException("valid side, price, and quantity are required");
        }
    }

    public boolean delete() {
        return quantity.signum() == 0;
    }
}
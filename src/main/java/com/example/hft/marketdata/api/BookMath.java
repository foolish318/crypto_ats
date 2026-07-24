package com.example.hft.marketdata.api;

import com.example.hft.marketdata.model.BookLevel;
import com.example.hft.marketdata.model.ExecutionSide;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;
import java.util.Optional;

public final class BookMath {
    private BookMath() {
    }

    public static BigDecimal availableQuantity(
            OrderBookView book,
            ExecutionSide side,
            int maxLevels
    ) {
        return levels(book, side, maxLevels).stream()
                .map(BookLevel::quantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public static Optional<BigDecimal> sweepPrice(
            OrderBookView book,
            ExecutionSide side,
            BigDecimal quantity
    ) {
        Fill fill = fill(book, side, quantity);
        return fill.complete ? Optional.of(fill.lastPrice) : Optional.empty();
    }

    public static Optional<BigDecimal> executableVwap(
            OrderBookView book,
            ExecutionSide side,
            BigDecimal quantity
    ) {
        Fill fill = fill(book, side, quantity);
        return fill.complete
                ? Optional.of(fill.notional.divide(quantity, MathContext.DECIMAL64))
                : Optional.empty();
    }

    private static Fill fill(
            OrderBookView book,
            ExecutionSide side,
            BigDecimal quantity
    ) {
        if (book == null || side == null || quantity == null || quantity.signum() <= 0) {
            throw new IllegalArgumentException("book, side, and positive quantity are required");
        }
        List<BookLevel> levels = levels(book, side, Integer.MAX_VALUE);
        BigDecimal remaining = quantity;
        BigDecimal notional = BigDecimal.ZERO;
        BigDecimal lastPrice = BigDecimal.ZERO;
        for (BookLevel level : levels) {
            BigDecimal fillQuantity = remaining.min(level.quantity());
            if (fillQuantity.signum() == 0) {
                continue;
            }
            notional = notional.add(fillQuantity.multiply(level.price()));
            remaining = remaining.subtract(fillQuantity);
            lastPrice = level.price();
            if (remaining.signum() == 0) {
                return new Fill(true, notional, lastPrice);
            }
        }
        return new Fill(false, notional, lastPrice);
    }

    private static List<BookLevel> levels(
            OrderBookView book,
            ExecutionSide side,
            int maxLevels
    ) {
        if (book == null || side == null || maxLevels < 0) {
            throw new IllegalArgumentException("book, side, and non-negative maxLevels are required");
        }
        return side == ExecutionSide.BUY
                ? book.topAsks(maxLevels)
                : book.topBids(maxLevels);
    }

    private record Fill(boolean complete, BigDecimal notional, BigDecimal lastPrice) {
    }
}
package com.example.hft.marketdata.model;

import java.math.BigDecimal;
import java.time.Instant;



public record TopOfBookSnapshot(
        String source,
        String exchange,
        String symbol,
        BigDecimal bidPrice,
        BigDecimal bidSize,
        BigDecimal askPrice,
        BigDecimal askSize,
        Instant sampledAt,
        long elapsedNanos
) {
    public BigDecimal spread() {
        return askPrice.subtract(bidPrice);
    }

    public String display() {
        return source
                + " exchange=" + exchange
                + " symbol=" + symbol
                + " bid=" + bidPrice.stripTrailingZeros().toPlainString()
                + "@" + bidSize.stripTrailingZeros().toPlainString()
                + " ask=" + askPrice.stripTrailingZeros().toPlainString()
                + "@" + askSize.stripTrailingZeros().toPlainString()
                + " spread=" + spread().stripTrailingZeros().toPlainString()
                + " elapsedMs=" + String.format("%.2f", elapsedNanos / 1_000_000.0);
    }
}

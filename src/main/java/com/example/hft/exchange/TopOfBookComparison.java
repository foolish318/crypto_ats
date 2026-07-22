package com.example.hft.exchange;

import com.example.hft.marketdata.model.TopOfBookSnapshot;
import java.math.BigDecimal;
import java.math.MathContext;



public final class TopOfBookComparison {
    private static final MathContext MATH = MathContext.DECIMAL64;

    private TopOfBookComparison() {
    }

    public static String compare(TopOfBookSnapshot custom, TopOfBookSnapshot xchange) {
        BigDecimal bidDiff = custom.bidPrice().subtract(xchange.bidPrice()).abs();
        BigDecimal askDiff = custom.askPrice().subtract(xchange.askPrice()).abs();
        BigDecimal bidBps = basisPoints(bidDiff, custom.bidPrice());
        BigDecimal askBps = basisPoints(askDiff, custom.askPrice());
        return "COMPARE exchange=" + custom.exchange()
                + " symbol=" + custom.symbol()
                + " bidDiff=" + bidDiff.stripTrailingZeros().toPlainString()
                + " bidDiffBps=" + format(bidBps)
                + " askDiff=" + askDiff.stripTrailingZeros().toPlainString()
                + " askDiffBps=" + format(askBps);
    }

    private static BigDecimal basisPoints(BigDecimal diff, BigDecimal price) {
        if (price.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return diff.divide(price, MATH).multiply(BigDecimal.valueOf(10_000), MATH);
    }

    private static String format(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}

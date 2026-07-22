package com.example.hft.app;

import com.example.hft.marketdata.model.Price;
import com.example.hft.marketdata.model.Quote;
import com.example.hft.marketdata.model.TradingSignal;
import com.example.hft.strategy.QuoteValidator;
import com.example.hft.strategy.TradingDecisionEngine;


public final class SelfTestMain {
    private SelfTestMain() {
    }

    public static void main(String[] args) {
        testPriceFormatting();
        testQuoteValidation();
        testDecisionEngine();
        System.out.println("self-tests passed");
    }

    private static void testPriceFormatting() {
        assertEquals("212.40", Price.fromTicks(21_240).toString(), "price formatting");
        assertThrows(() -> Price.fromTicks(0), "zero price should fail");
    }

    private static void testQuoteValidation() {
        QuoteValidator validator = new QuoteValidator();
        validator.validate(Quote.of(1, "BARC.L", 21_240, 1_000, 21_246, 800, 0));
        assertThrows(() -> validator.validate(Quote.of(2, "BARC.L", 21_246, 1_000, 21_246, 800, 0)), "locked quote should fail");
        assertThrows(() -> validator.validate(Quote.of(3, "", 21_240, 1_000, 21_246, 800, 0)), "blank symbol should fail");
    }

    private static void testDecisionEngine() {
        TradingDecisionEngine engine = new TradingDecisionEngine();
        assertEquals(TradingSignal.BUY_PRESSURE, engine.evaluate(Quote.of(1, "BARC.L", 21_240, 2_000, 21_244, 500, 0)), "buy pressure");
        assertEquals(TradingSignal.SELL_PRESSURE, engine.evaluate(Quote.of(2, "BARC.L", 21_240, 500, 21_244, 2_000, 0)), "sell pressure");
        assertEquals(TradingSignal.DO_NOT_TRADE, engine.evaluate(Quote.of(3, "BARC.L", 21_240, 2_000, 21_260, 500, 0)), "wide spread");
    }

    private static void assertEquals(Object expected, Object actual, String label) {
        if (!expected.equals(actual)) {
            throw new AssertionError(label + ": expected=" + expected + " actual=" + actual);
        }
    }

    private static void assertThrows(Runnable action, String label) {
        try {
            action.run();
        } catch (RuntimeException expected) {
            return;
        }
        throw new AssertionError(label + ": expected exception");
    }
}

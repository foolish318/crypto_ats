package com.example.hft.app;

import com.example.hft.datasource.book.BookQuality;
import com.example.hft.datasource.book.DepthUpdateApplyResult;
import com.example.hft.datasource.book.SequencedLocalOrderBook;
import com.example.hft.exchange.binance.BinanceDepthParser;
import com.example.hft.marketdata.model.DepthBookTop;
import com.example.hft.marketdata.model.Price;
import com.example.hft.marketdata.model.Quote;
import com.example.hft.marketdata.model.TradingSignal;
import com.example.hft.strategy.QuoteValidator;
import com.example.hft.strategy.TradingDecisionEngine;


public final class SelfTestMain {
    private SelfTestMain() {
    }

    public static void main(String[] args) throws Exception {
        testPriceFormatting();
        testQuoteValidation();
        testDecisionEngine();
        testSequencedLocalOrderBook();
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

    private static void testSequencedLocalOrderBook() throws Exception {
        SequencedLocalOrderBook book = new SequencedLocalOrderBook("XRPUSDT");
        book.loadSnapshot("{\"lastUpdateId\":100,\"bids\":[[\"1.14060\",\"10.0000\"]],\"asks\":[[\"1.14080\",\"12.0000\"]]}");
        BinanceDepthParser parser = new BinanceDepthParser();

        DepthUpdateApplyResult first = book.apply(parser.parseUpdate("{\"e\":\"depthUpdate\",\"E\":1,\"s\":\"XRPUSDT\",\"U\":99,\"u\":101,\"b\":[[\"1.14070\",\"10.0000\"]],\"a\":[[\"1.14090\",\"12.0000\"]]}"));
        assertEquals(DepthUpdateApplyResult.APPLIED, first, "snapshot bridge should apply");
        assertEquals(BookQuality.LIVE, book.quality(), "book should be live after bridged update");

        DepthUpdateApplyResult stale = book.apply(parser.parseUpdate("{\"e\":\"depthUpdate\",\"E\":2,\"s\":\"XRPUSDT\",\"U\":98,\"u\":100,\"b\":[],\"a\":[]}"));
        assertEquals(DepthUpdateApplyResult.STALE, stale, "old update should be stale");

        DepthUpdateApplyResult second = book.apply(parser.parseUpdate("{\"e\":\"depthUpdate\",\"E\":3,\"s\":\"XRPUSDT\",\"U\":102,\"u\":102,\"b\":[],\"a\":[]}"));
        assertEquals(DepthUpdateApplyResult.APPLIED, second, "next contiguous update should apply");

        DepthUpdateApplyResult gap = book.apply(parser.parseUpdate("{\"e\":\"depthUpdate\",\"E\":4,\"s\":\"XRPUSDT\",\"U\":104,\"u\":104,\"b\":[],\"a\":[]}"));
        assertEquals(DepthUpdateApplyResult.GAP, gap, "missing update id should be gap");
        assertEquals(BookQuality.GAP_DETECTED, book.quality(), "gap should degrade book quality");

        DepthBookTop top = book.topLevels(1);
        if (top.bidPrices()[0] >= top.askPrices()[0]) {
            throw new AssertionError("crypto decimal price scale should keep bid below ask");
        }
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
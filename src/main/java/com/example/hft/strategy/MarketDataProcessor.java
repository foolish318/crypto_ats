package com.example.hft.strategy;

import com.example.hft.benchmark.ModuleTiming;
import com.example.hft.marketdata.model.Quote;
import com.example.hft.marketdata.model.QuoteAnalysis;
import com.example.hft.marketdata.model.TradingSignal;


public final class MarketDataProcessor {
    private final QuoteValidator validator;
    private final TradingDecisionEngine decisionEngine;

    public MarketDataProcessor(QuoteValidator validator, TradingDecisionEngine decisionEngine) {
        this.validator = validator;
        this.decisionEngine = decisionEngine;
    }

    public TradingSignal signalFor(Quote quote) {
        validator.validate(quote);
        return decisionEngine.evaluate(quote);
    }

    public TradingSignal signalFor(Quote quote, ModuleTiming timing) {
        long validationStart = System.nanoTime();
        validator.validate(quote);
        long decisionStart = System.nanoTime();
        TradingSignal signal = decisionEngine.evaluate(quote);
        long decisionEnd = System.nanoTime();

        timing.addValidationNanos(decisionStart - validationStart);
        timing.addDecisionNanos(decisionEnd - decisionStart);
        return signal;
    }

    public QuoteAnalysis analyze(Quote quote) {
        TradingSignal signal = signalFor(quote);
        return new QuoteAnalysis(quote, quote.midPrice(), quote.spread(), signal);
    }

    public QuoteAnalysis analyze(Quote quote, ModuleTiming timing) {
        TradingSignal signal = signalFor(quote, timing);
        long analysisStart = System.nanoTime();
        QuoteAnalysis analysis = new QuoteAnalysis(quote, quote.midPrice(), quote.spread(), signal);
        timing.addAnalysisNanos(System.nanoTime() - analysisStart);
        return analysis;
    }
}

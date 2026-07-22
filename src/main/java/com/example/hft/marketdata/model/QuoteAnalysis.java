package com.example.hft.marketdata.model;


public final class QuoteAnalysis {
    private final Quote quote;
    private final Price midPrice;
    private final Price spread;
    private final TradingSignal signal;

    public QuoteAnalysis(Quote quote, Price midPrice, Price spread, TradingSignal signal) {
        this.quote = quote;
        this.midPrice = midPrice;
        this.spread = spread;
        this.signal = signal;
    }

    public TradingSignal signal() {
        return signal;
    }

    public String toDisplayLine() {
        return quote.symbol()
                + " bid=" + quote.bidSize() + "@" + quote.bidPrice()
                + " ask=" + quote.askSize() + "@" + quote.askPrice()
                + " mid=" + midPrice
                + " spread=" + spread
                + " signal=" + signal;
    }
}

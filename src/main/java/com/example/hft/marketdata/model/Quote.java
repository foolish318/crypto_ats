package com.example.hft.marketdata.model;

import java.util.Objects;



public final class Quote {
    private final long sequenceNumber;
    private final String symbol;
    private final Price bidPrice;
    private final int bidSize;
    private final Price askPrice;
    private final int askSize;
    private final long receivedNanos;

    public Quote(long sequenceNumber, String symbol, Price bidPrice, int bidSize, Price askPrice, int askSize, long receivedNanos) {
        if (sequenceNumber < 0) {
            throw new IllegalArgumentException("sequence number must not be negative");
        }
        this.sequenceNumber = sequenceNumber;
        this.symbol = Objects.requireNonNull(symbol, "symbol");
        this.bidPrice = Objects.requireNonNull(bidPrice, "bidPrice");
        this.bidSize = bidSize;
        this.askPrice = Objects.requireNonNull(askPrice, "askPrice");
        this.askSize = askSize;
        this.receivedNanos = receivedNanos;
    }

    public static Quote of(long sequenceNumber, String symbol, long bidTicks, int bidSize, long askTicks, int askSize, long receivedNanos) {
        return new Quote(sequenceNumber, symbol, Price.fromTicks(bidTicks), bidSize, Price.fromTicks(askTicks), askSize, receivedNanos);
    }

    public long sequenceNumber() {
        return sequenceNumber;
    }

    public String symbol() {
        return symbol;
    }

    public Price bidPrice() {
        return bidPrice;
    }

    public int bidSize() {
        return bidSize;
    }

    public Price askPrice() {
        return askPrice;
    }

    public int askSize() {
        return askSize;
    }

    public long receivedNanos() {
        return receivedNanos;
    }

    public long midTicks() {
        return (bidPrice.ticks() + askPrice.ticks()) / 2;
    }

    public long spreadTicks() {
        return askPrice.ticks() - bidPrice.ticks();
    }

    public Price midPrice() {
        return Price.fromRawTicks(midTicks());
    }

    public Price spread() {
        return Price.fromRawTicks(spreadTicks());
    }
}

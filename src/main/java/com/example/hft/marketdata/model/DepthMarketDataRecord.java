package com.example.hft.marketdata.model;

import java.util.Arrays;



public final class DepthMarketDataRecord {
    private final long sequenceNumber;
    private final String symbol;
    private final long exchangeEventTimeMillis;
    private final long localReceivedEpochMillis;
    private final long rawReceivedNanos;
    private final long parsedNanos;
    private final long bookUpdatedNanos;
    private final long[] bidPrices;
    private final int[] bidSizes;
    private final long[] askPrices;
    private final int[] askSizes;
    private final String rawPayload;

    public DepthMarketDataRecord(long sequenceNumber, String symbol, long exchangeEventTimeMillis,
                                 long localReceivedEpochMillis, long rawReceivedNanos, long parsedNanos,
                                 long bookUpdatedNanos, long[] bidPrices, int[] bidSizes, long[] askPrices,
                                 int[] askSizes, String rawPayload) {
        this.sequenceNumber = sequenceNumber;
        this.symbol = symbol;
        this.exchangeEventTimeMillis = exchangeEventTimeMillis;
        this.localReceivedEpochMillis = localReceivedEpochMillis;
        this.rawReceivedNanos = rawReceivedNanos;
        this.parsedNanos = parsedNanos;
        this.bookUpdatedNanos = bookUpdatedNanos;
        this.bidPrices = Arrays.copyOf(bidPrices, bidPrices.length);
        this.bidSizes = Arrays.copyOf(bidSizes, bidSizes.length);
        this.askPrices = Arrays.copyOf(askPrices, askPrices.length);
        this.askSizes = Arrays.copyOf(askSizes, askSizes.length);
        this.rawPayload = rawPayload;
    }

    public long sequenceNumber() {
        return sequenceNumber;
    }

    public String symbol() {
        return symbol;
    }

    public long exchangeEventTimeMillis() {
        return exchangeEventTimeMillis;
    }

    public long localReceivedEpochMillis() {
        return localReceivedEpochMillis;
    }

    public long rawReceivedNanos() {
        return rawReceivedNanos;
    }

    public long parsedNanos() {
        return parsedNanos;
    }

    public long bookUpdatedNanos() {
        return bookUpdatedNanos;
    }

    public long bidPrice(int level) {
        return bidPrices[level];
    }

    public int bidSize(int level) {
        return bidSizes[level];
    }

    public long askPrice(int level) {
        return askPrices[level];
    }

    public int askSize(int level) {
        return askSizes[level];
    }

    public int levels() {
        return Math.min(Math.min(bidPrices.length, askPrices.length), Math.min(bidSizes.length, askSizes.length));
    }

    public long topSpreadTicks() {
        return askPrices[0] - bidPrices[0];
    }

    public long spreadTicksAtLevel(int levelCount) {
        int index = Math.min(levelCount, levels()) - 1;
        return askPrices[index] - bidPrices[index];
    }

    public long bidVolume(int levelCount) {
        long total = 0;
        int limit = Math.min(levelCount, levels());
        for (int i = 0; i < limit; i++) {
            total += bidSizes[i];
        }
        return total;
    }

    public long askVolume(int levelCount) {
        long total = 0;
        int limit = Math.min(levelCount, levels());
        for (int i = 0; i < limit; i++) {
            total += askSizes[i];
        }
        return total;
    }

    public long weightedBidTicks(int levelCount) {
        return weightedPrice(bidPrices, bidSizes, levelCount);
    }

    public long weightedAskTicks(int levelCount) {
        return weightedPrice(askPrices, askSizes, levelCount);
    }

    public long[] bidPricesCopy() {
        return Arrays.copyOf(bidPrices, bidPrices.length);
    }

    public int[] bidSizesCopy() {
        return Arrays.copyOf(bidSizes, bidSizes.length);
    }

    public long[] askPricesCopy() {
        return Arrays.copyOf(askPrices, askPrices.length);
    }

    public int[] askSizesCopy() {
        return Arrays.copyOf(askSizes, askSizes.length);
    }

    public String rawPayload() {
        return rawPayload;
    }

    private static long weightedPrice(long[] prices, int[] sizes, int levelCount) {
        long notional = 0;
        long volume = 0;
        int limit = Math.min(levelCount, Math.min(prices.length, sizes.length));
        for (int i = 0; i < limit; i++) {
            notional += prices[i] * sizes[i];
            volume += sizes[i];
        }
        return volume == 0 ? 0 : notional / volume;
    }
}

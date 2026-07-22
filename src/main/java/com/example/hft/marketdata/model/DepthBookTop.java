package com.example.hft.marketdata.model;


public final class DepthBookTop {
    private final long[] bidPrices;
    private final int[] bidSizes;
    private final long[] askPrices;
    private final int[] askSizes;

    public DepthBookTop(long[] bidPrices, int[] bidSizes, long[] askPrices, int[] askSizes) {
        this.bidPrices = bidPrices;
        this.bidSizes = bidSizes;
        this.askPrices = askPrices;
        this.askSizes = askSizes;
    }

    public long[] bidPrices() {
        return bidPrices;
    }

    public int[] bidSizes() {
        return bidSizes;
    }

    public long[] askPrices() {
        return askPrices;
    }

    public int[] askSizes() {
        return askSizes;
    }
}

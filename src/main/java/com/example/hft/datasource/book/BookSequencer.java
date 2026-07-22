package com.example.hft.datasource.book;

import com.example.hft.datasource.normalizer.NormalizedMarketDataEvent;


public final class BookSequencer {
    private long lastSequence = -1L;
    private BookQuality quality = BookQuality.EMPTY;

    public boolean accept(NormalizedMarketDataEvent event) {
        long sequence = event.sequence();
        if (sequence < 0) {
            quality = BookQuality.LIVE;
            return true;
        }
        if (lastSequence >= 0 && sequence <= lastSequence) {
            quality = BookQuality.DEGRADED;
            return false;
        }
        if (lastSequence >= 0 && sequence != lastSequence + 1) {
            quality = BookQuality.GAP_DETECTED;
            return false;
        }
        lastSequence = sequence;
        quality = BookQuality.LIVE;
        return true;
    }

    public BookQuality quality() {
        return quality;
    }

    public long lastSequence() {
        return lastSequence;
    }

    public void reset() {
        lastSequence = -1L;
        quality = BookQuality.BOOTSTRAPPING;
    }
}

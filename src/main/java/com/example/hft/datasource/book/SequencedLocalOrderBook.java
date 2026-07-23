package com.example.hft.datasource.book;

import com.example.hft.marketdata.model.DepthBookTop;
import com.example.hft.marketdata.model.DepthUpdate;
import com.example.hft.marketdata.model.LocalOrderBook;
import java.util.Locale;


public final class SequencedLocalOrderBook {
    private final String symbol;
    private final LocalOrderBook book = new LocalOrderBook();

    private BookQuality quality = BookQuality.EMPTY;
    private boolean bootstrapped;
    private long snapshotLastUpdateId;
    private long applied;
    private long stale;
    private long gaps;
    private long crossed;
    private long unknownSymbol;

    public SequencedLocalOrderBook(String symbol) {
        this.symbol = symbol.toUpperCase(Locale.ROOT);
    }

    public void loadSnapshot(String payload) throws Exception {
        book.loadSnapshot(payload);
        snapshotLastUpdateId = book.lastUpdateId();
        bootstrapped = false;
        quality = BookQuality.BOOTSTRAPPING;
    }

    public DepthUpdateApplyResult apply(DepthUpdate update) {
        if (!symbol.equals(update.symbol())) {
            unknownSymbol++;
            quality = BookQuality.DEGRADED;
            return DepthUpdateApplyResult.UNKNOWN_SYMBOL;
        }

        long lastUpdateId = book.lastUpdateId();
        if (update.finalUpdateId() <= lastUpdateId) {
            stale++;
            return DepthUpdateApplyResult.STALE;
        }

        boolean contiguous;
        if (!bootstrapped) {
            long nextUpdateId = lastUpdateId + 1;
            contiguous = update.firstUpdateId() <= nextUpdateId && update.finalUpdateId() >= nextUpdateId;
        } else {
            contiguous = update.firstUpdateId() == lastUpdateId + 1;
        }

        if (!contiguous) {
            gaps++;
            quality = BookQuality.GAP_DETECTED;
            return DepthUpdateApplyResult.GAP;
        }

        boolean appliedUpdate = book.applyDepthUpdate(update);
        if (!appliedUpdate) {
            stale++;
            return DepthUpdateApplyResult.STALE;
        }

        bootstrapped = true;
        applied++;
        DepthBookTop top = book.topLevels(1);
        if (top.bidPrices()[0] > 0 && top.askPrices()[0] > 0 && top.bidPrices()[0] >= top.askPrices()[0]) {
            crossed++;
            quality = BookQuality.CROSSED;
            return DepthUpdateApplyResult.CROSSED;
        }

        quality = BookQuality.LIVE;
        return DepthUpdateApplyResult.APPLIED;
    }

    public DepthBookTop topLevels(int levels) {
        return book.topLevels(levels);
    }

    public String symbol() {
        return symbol;
    }

    public long snapshotLastUpdateId() {
        return snapshotLastUpdateId;
    }

    public long lastUpdateId() {
        return book.lastUpdateId();
    }

    public BookQuality quality() {
        return quality;
    }

    public long applied() {
        return applied;
    }

    public long stale() {
        return stale;
    }

    public long gaps() {
        return gaps;
    }

    public long crossed() {
        return crossed;
    }

    public long unknownSymbol() {
        return unknownSymbol;
    }
}
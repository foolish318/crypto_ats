package com.example.hft.datasource.deepbook.runtime;

import com.example.hft.datasource.book.BookQuality;
import java.time.Instant;
import java.util.List;


public record LocalBookSnapshot(
        String sourceId,
        String exchange,
        String symbol,
        BookQuality quality,
        long sequence,
        Instant exchangeTime,
        List<DecimalBookLevel> bids,
        List<DecimalBookLevel> asks
) {
    public LocalBookSnapshot {
        bids = List.copyOf(bids);
        asks = List.copyOf(asks);
    }

    public DecimalBookLevel bestBid() {
        return bids.isEmpty() ? null : bids.get(0);
    }

    public DecimalBookLevel bestAsk() {
        return asks.isEmpty() ? null : asks.get(0);
    }
}

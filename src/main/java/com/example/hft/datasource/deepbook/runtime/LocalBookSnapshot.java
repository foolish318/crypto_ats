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
        long bookVersion,
        Instant exchangeTime,
        Instant lastReceiveTime,
        Instant lastAppliedTime,
        List<DecimalBookLevel> bids,
        List<DecimalBookLevel> asks
) {
    public LocalBookSnapshot {
        if (bookVersion < 0L || exchangeTime == null
                || lastReceiveTime == null || lastAppliedTime == null) {
            throw new IllegalArgumentException("book version and timestamps are required");
        }
        bids = List.copyOf(bids);
        asks = List.copyOf(asks);
    }

    public LocalBookSnapshot(
            String sourceId,
            String exchange,
            String symbol,
            BookQuality quality,
            long sequence,
            Instant exchangeTime,
            List<DecimalBookLevel> bids,
            List<DecimalBookLevel> asks
    ) {
        this(sourceId, exchange, symbol, quality, sequence, 0L,
                exchangeTime, exchangeTime, exchangeTime, bids, asks);
    }

    public DecimalBookLevel bestBid() {
        return bids.isEmpty() ? null : bids.get(0);
    }

    public DecimalBookLevel bestAsk() {
        return asks.isEmpty() ? null : asks.get(0);
    }

    public long ageMillis(long observedEpochMillis) {
        return Math.max(0L, observedEpochMillis - lastReceiveTime.toEpochMilli());
    }
}
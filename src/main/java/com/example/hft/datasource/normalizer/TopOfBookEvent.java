package com.example.hft.datasource.normalizer;

import com.example.hft.datasource.transport.TransportType;
import com.example.hft.marketdata.model.TopOfBookSnapshot;
import java.math.BigDecimal;
import java.time.Instant;


public record TopOfBookEvent(
        String source,
        String exchange,
        String symbol,
        TransportType transport,
        long receivedNanos,
        long exchangeTimeMillis,
        long sequence,
        Instant sampledAt,
        long sourceElapsedNanos,
        BigDecimal bidPrice,
        BigDecimal bidSize,
        BigDecimal askPrice,
        BigDecimal askSize
) implements NormalizedMarketDataEvent {
    private static final long UNKNOWN_EXCHANGE_TIME = -1L;
    private static final long UNKNOWN_SEQUENCE = -1L;

    public static TopOfBookEvent from(TopOfBookSnapshot snapshot, TransportType transport) {
        return new TopOfBookEvent(
                snapshot.source(),
                snapshot.exchange(),
                snapshot.symbol(),
                transport,
                System.nanoTime(),
                UNKNOWN_EXCHANGE_TIME,
                UNKNOWN_SEQUENCE,
                snapshot.sampledAt(),
                snapshot.elapsedNanos(),
                snapshot.bidPrice(),
                snapshot.bidSize(),
                snapshot.askPrice(),
                snapshot.askSize()
        );
    }

    public BigDecimal spread() {
        return askPrice.subtract(bidPrice);
    }

    public TopOfBookSnapshot toSnapshot() {
        return new TopOfBookSnapshot(source, exchange, symbol, bidPrice, bidSize, askPrice, askSize,
                sampledAt, sourceElapsedNanos);
    }
}
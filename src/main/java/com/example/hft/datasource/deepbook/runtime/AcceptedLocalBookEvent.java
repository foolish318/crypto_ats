package com.example.hft.datasource.deepbook.runtime;

import com.example.hft.datasource.book.BookQuality;
import com.example.hft.datasource.normalizer.NormalizedMarketDataEvent;
import com.example.hft.datasource.transport.TransportType;


public record AcceptedLocalBookEvent(
        String source,
        String exchange,
        String symbol,
        TransportType transport,
        long receivedNanos,
        long exchangeTimeMillis,
        long sequence,
        long generation,
        long acceptedEpochMillis,
        LocalBookSnapshot book
) implements NormalizedMarketDataEvent {
    public AcceptedLocalBookEvent {
        if (source == null || source.isBlank()
                || exchange == null || exchange.isBlank()
                || symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("accepted book event requires source, exchange, and symbol");
        }
        if (book == null || book.quality() != BookQuality.LIVE) {
            throw new IllegalArgumentException("accepted book event requires a LIVE local book");
        }
        if (!exchange.equals(book.exchange()) || !symbol.equals(book.symbol())) {
            throw new IllegalArgumentException("event identity must match local book identity");
        }
    }
}

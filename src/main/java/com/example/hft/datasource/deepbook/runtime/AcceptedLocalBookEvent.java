package com.example.hft.datasource.deepbook.runtime;

import com.example.hft.datasource.book.BookQuality;
import com.example.hft.datasource.normalizer.NormalizedMarketDataEvent;
import com.example.hft.datasource.transport.TransportType;

public record AcceptedLocalBookEvent(
        String source,
        String exchange,
        String symbol,
        String canonicalInstrumentId,
        TransportType transport,
        long receivedNanos,
        long exchangeTimeMillis,
        long sequence,
        long generation,
        long acceptedEpochMillis,
        long localSequence,
        long publishNanos,
        LocalBookSnapshot book
) implements NormalizedMarketDataEvent {
    public AcceptedLocalBookEvent {
        if (source == null || source.isBlank()
                || exchange == null || exchange.isBlank()
                || symbol == null || symbol.isBlank()
                || canonicalInstrumentId == null || canonicalInstrumentId.isBlank()) {
            throw new IllegalArgumentException(
                    "accepted book event requires source, exchange, symbol, and canonical instrument");
        }
        if (book == null || book.quality() != BookQuality.LIVE) {
            throw new IllegalArgumentException("accepted book event requires a LIVE local book");
        }
        if (!exchange.equals(book.exchange()) || !symbol.equals(book.symbol())) {
            throw new IllegalArgumentException("event identity must match local book identity");
        }
        if (localSequence < 0L || publishNanos < 0L) {
            throw new IllegalArgumentException("local sequence and publish clock must be non-negative");
        }
    }

    public AcceptedLocalBookEvent(
            String source,
            String exchange,
            String symbol,
            String canonicalInstrumentId,
            TransportType transport,
            long receivedNanos,
            long exchangeTimeMillis,
            long sequence,
            long generation,
            long acceptedEpochMillis,
            LocalBookSnapshot book
    ) {
        this(source, exchange, symbol, canonicalInstrumentId, transport,
                receivedNanos, exchangeTimeMillis, sequence, generation,
                acceptedEpochMillis,
                book == null ? Math.max(0L, sequence) : Math.max(0L, book.bookVersion()),
                Math.max(0L, receivedNanos),
                book);
    }
}
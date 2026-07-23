package com.example.hft.datasource.deepbook.runtime;

import com.example.hft.datasource.book.BookQuality;
import com.example.hft.datasource.engine.MarketDataEngine;
import com.example.hft.datasource.transport.TransportType;
import java.util.function.LongSupplier;


public final class LocalBookPublisher {
    private final MarketDataEngine engine;
    private final long staleThresholdMillis;
    private final int publishedDepth;
    private final LongSupplier clock;

    public LocalBookPublisher(
            MarketDataEngine engine,
            long staleThresholdMillis,
            int publishedDepth
    ) {
        this(engine, staleThresholdMillis, publishedDepth, System::currentTimeMillis);
    }

    LocalBookPublisher(
            MarketDataEngine engine,
            long staleThresholdMillis,
            int publishedDepth,
            LongSupplier clock
    ) {
        if (staleThresholdMillis <= 0L || publishedDepth <= 0) {
            throw new IllegalArgumentException("publisher thresholds must be positive");
        }
        this.engine = engine;
        this.staleThresholdMillis = staleThresholdMillis;
        this.publishedDepth = publishedDepth;
        this.clock = clock;
    }

    public boolean publishIfEligible(
            LocalOrderBookBuilder builder,
            BookUpdateResult result,
            SessionHealth health,
            long generation,
            long receivedNanos,
            long acceptedEpochMillis
    ) {
        if (!result.accepted()
                || result.quality() != BookQuality.LIVE
                || !health.publishable(clock.getAsLong(), staleThresholdMillis)) {
            return false;
        }
        LocalBookSnapshot snapshot = builder.snapshot(publishedDepth);
        if (snapshot.quality() != BookQuality.LIVE) {
            return false;
        }
        engine.onEvent(new AcceptedLocalBookEvent(
                snapshot.sourceId(),
                snapshot.exchange(),
                snapshot.symbol(),
                TransportType.WEBSOCKET,
                receivedNanos,
                result.eventTimeMillis(),
                result.sequence(),
                generation,
                acceptedEpochMillis,
                snapshot
        ));
        return true;
    }
}

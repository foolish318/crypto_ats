package com.example.hft.datasource.engine;

import com.example.hft.datasource.DataSourceHealth;
import com.example.hft.datasource.DataSourceStatus;
import com.example.hft.datasource.MarketDataSink;
import com.example.hft.datasource.deepbook.runtime.AcceptedLocalBookEvent;
import com.example.hft.datasource.deepbook.runtime.BookAvailabilityEvent;
import com.example.hft.datasource.deepbook.runtime.BookAvailabilityState;
import com.example.hft.datasource.normalizer.NormalizedMarketDataEvent;
import java.time.Instant;


public final class MarketDataEngine implements MarketDataSink {
    private final MarketDataCache cache;
    private final MarketDataEventBus eventBus;

    public MarketDataEngine(MarketDataCache cache, MarketDataEventBus eventBus) {
        this.cache = cache;
        this.eventBus = eventBus;
    }

    @Override
    public void onEvent(NormalizedMarketDataEvent event) {
        if (event instanceof AcceptedLocalBookEvent accepted) {
            publishAccepted(accepted);
            return;
        }
        cache.update(event);
        eventBus.publish(event);
    }


    public EnginePublishResult publishAccepted(AcceptedLocalBookEvent accepted) {
        long cacheStarted = System.nanoTime();
        boolean current = cache.updateAccepted(accepted);
        long cacheNanos = System.nanoTime() - cacheStarted;
        if (!current) {
            return EnginePublishResult.rejected(cacheNanos);
        }
        EventBusPublishResult marketData = eventBus.publishMeasured(accepted);
        EventBusPublishResult availability = eventBus.publishAvailabilityMeasured(
                new BookAvailabilityEvent(
                        accepted.source(), accepted.exchange(), accepted.symbol(),
                        accepted.canonicalInstrumentId(), accepted.generation(),
                        BookAvailabilityState.LIVE, "quality gate accepted",
                        Instant.ofEpochMilli(accepted.acceptedEpochMillis())
                )
        );
        EventBusPublishResult listeners = marketData.plus(availability);
        return new EnginePublishResult(
                true,
                cacheNanos,
                listeners.coreListenerNanos(),
                listeners.asyncOfferNanos()
        );
    }
    public void onAvailability(BookAvailabilityEvent event) {
        if (cache.invalidate(event)) {
            eventBus.publishAvailability(event);
        }
    }

    @Override
    public void onHealth(DataSourceHealth health) {
        MarketDataCache.BookIdentity identity = cache.identity(health.source())
                .orElse(new MarketDataCache.BookIdentity(
                        health.source(), health.exchange(), health.symbol(), health.symbol(),
                        cache.latestGeneration(health.source()), null
                ));
        onAvailability(new BookAvailabilityEvent(
                identity.sourceId(), identity.exchange(), identity.venueSymbol(),
                identity.canonicalInstrumentId(), identity.generation(),
                map(health.status()), health.detail(), health.observedAt()
        ));
    }

    @Override
    public void onError(String source, Throwable error) {
        cache.identity(source).ifPresent(identity -> onAvailability(new BookAvailabilityEvent(
                identity.sourceId(), identity.exchange(), identity.venueSymbol(),
                identity.canonicalInstrumentId(), identity.generation(),
                BookAvailabilityState.INVALID,
                error == null
                        ? "unknown source error"
                        : error.getClass().getSimpleName() + ": " + error.getMessage(),
                Instant.now()
        )));
    }

    public MarketDataCache cache() {
        return cache;
    }

    private static BookAvailabilityState map(DataSourceStatus status) {
        return switch (status) {
            case LIVE -> BookAvailabilityState.LIVE;
            case CONNECTING -> BookAvailabilityState.RECOVERING;
            case STOPPED -> BookAvailabilityState.STOPPED;
            case CREATED, DEGRADED -> BookAvailabilityState.STALE;
        };
    }
}

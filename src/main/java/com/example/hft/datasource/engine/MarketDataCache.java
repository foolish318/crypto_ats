package com.example.hft.datasource.engine;

import com.example.hft.datasource.deepbook.runtime.AcceptedLocalBookEvent;
import com.example.hft.datasource.deepbook.runtime.BookAvailabilityEvent;
import com.example.hft.datasource.deepbook.runtime.BookAvailabilityState;
import com.example.hft.marketdata.model.PublicTrade;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;


public final class MarketDataCache {
    private final Map<String, AcceptedLocalBookEvent> deepBookByVenueSymbol =
            new ConcurrentHashMap<>();
    private final Map<String, SourceFence> sourceFences = new ConcurrentHashMap<>();
    private final Map<String, PublicTrade> latestTrades = new ConcurrentHashMap<>();

    public boolean updateAccepted(AcceptedLocalBookEvent event) {
        SourceFence fence = sourceFences.computeIfAbsent(event.source(), ignored -> new SourceFence());
        synchronized (fence) {
            if (event.generation() < fence.generation) {
                return false;
            }
            if (event.generation() == fence.generation
                    && fence.state != null
                    && fence.state != BookAvailabilityState.LIVE
                    && !fence.restoreAllowed) {
                return false;
            }
            fence.generation = event.generation();
            fence.state = BookAvailabilityState.LIVE;
            fence.exchange = event.exchange();
            fence.symbol = event.symbol();
            fence.canonicalInstrumentId = event.canonicalInstrumentId();
            fence.restoreAllowed = false;
            deepBookByVenueSymbol.put(key(event.exchange(), event.symbol()), event);
            return true;
        }
    }

    public boolean invalidate(BookAvailabilityEvent event) {
        if (event.live()) {
            return false;
        }
        SourceFence fence = sourceFences.computeIfAbsent(event.sourceId(), ignored -> new SourceFence());
        synchronized (fence) {
            if (event.generation() < fence.generation) {
                return false;
            }
            boolean newerGeneration = event.generation() > fence.generation;
            fence.generation = event.generation();
            fence.state = event.state();
            fence.exchange = event.exchange();
            fence.symbol = event.venueSymbol();
            fence.canonicalInstrumentId = event.canonicalInstrumentId();
            fence.restoreAllowed = newerGeneration
                    && event.state() == BookAvailabilityState.RECOVERING;
            if (!event.live()) {
                String key = key(event.exchange(), event.venueSymbol());
                AcceptedLocalBookEvent cached = deepBookByVenueSymbol.get(key);
                if (cached != null && cached.generation() <= event.generation()) {
                    deepBookByVenueSymbol.remove(key, cached);
                }
            }
            return true;
        }
    }

    public Optional<AcceptedLocalBookEvent> deepBook(String exchange, String symbol) {
        AcceptedLocalBookEvent event = deepBookByVenueSymbol.get(key(exchange, symbol));
        if (event == null) {
            return Optional.empty();
        }
        SourceFence fence = sourceFences.get(event.source());
        if (fence == null) {
            return Optional.empty();
        }
        synchronized (fence) {
            return fence.state == BookAvailabilityState.LIVE
                    && fence.generation == event.generation()
                    ? Optional.of(event)
                    : Optional.empty();
        }
    }

    public Optional<BookIdentity> identity(String sourceId) {
        SourceFence fence = sourceFences.get(sourceId);
        if (fence == null) {
            return Optional.empty();
        }
        synchronized (fence) {
            if (fence.exchange == null) {
                return Optional.empty();
            }
            return Optional.of(new BookIdentity(
                    sourceId,
                    fence.exchange,
                    fence.symbol,
                    fence.canonicalInstrumentId,
                    fence.generation,
                    fence.state
            ));
        }
    }

    public long latestGeneration(String sourceId) {
        SourceFence fence = sourceFences.get(sourceId);
        if (fence == null) {
            return 0L;
        }
        synchronized (fence) {
            return Math.max(0L, fence.generation);
        }
    }



    public boolean updateTrade(PublicTrade trade) {
        String key = trade.exchange() + "|" + trade.header().instrumentId().value();
        PublicTrade updated = latestTrades.compute(key, (ignored, current) -> {
            if (current == null) {
                return trade;
            }
            long currentEpoch = current.header().streamEpoch();
            long nextEpoch = trade.header().streamEpoch();
            if (nextEpoch < currentEpoch
                    || nextEpoch == currentEpoch
                    && trade.header().localSequence() <= current.header().localSequence()) {
                return current;
            }
            return trade;
        });
        return updated == trade;
    }

    public Optional<PublicTrade> latestTrade(String exchange, String canonicalInstrumentId) {
        return Optional.ofNullable(latestTrades.get(exchange + "|" + canonicalInstrumentId));
    }

    public int tradeCount() {
        return latestTrades.size();
    }
    public int deepBookCount() {
        return deepBookByVenueSymbol.size();
    }

    private static String key(String exchange, String symbol) {
        return exchange + "|" + symbol;
    }

    public record BookIdentity(
            String sourceId,
            String exchange,
            String venueSymbol,
            String canonicalInstrumentId,
            long generation,
            BookAvailabilityState state
    ) {
    }

    private static final class SourceFence {
        private long generation = -1L;
        private BookAvailabilityState state;
        private String exchange;
        private String symbol;
        private String canonicalInstrumentId;
        private boolean restoreAllowed;
    }
}

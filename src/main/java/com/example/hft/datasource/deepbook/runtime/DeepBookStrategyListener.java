package com.example.hft.datasource.deepbook.runtime;

import com.example.hft.datasource.engine.MarketDataListener;
import com.example.hft.datasource.normalizer.NormalizedMarketDataEvent;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;


public final class DeepBookStrategyListener implements MarketDataListener {
    private final AtomicLong acceptedBooks = new AtomicLong();
    private final AtomicLong usableBooks = new AtomicLong();
    private final Map<String, Long> activeGenerations = new ConcurrentHashMap<>();

    @Override
    public void onMarketData(NormalizedMarketDataEvent event) {
        if (!(event instanceof AcceptedLocalBookEvent accepted)) {
            return;
        }
        activeGenerations.compute(accepted.source(), (ignored, current) ->
                current == null || accepted.generation() >= current
                        ? accepted.generation()
                        : current);
        acceptedBooks.incrementAndGet();
        DecimalBookLevel bid = accepted.book().bestBid();
        DecimalBookLevel ask = accepted.book().bestAsk();
        if (bid != null && ask != null && bid.price().compareTo(ask.price()) < 0) {
            usableBooks.incrementAndGet();
        }
    }


    @Override
    public void onBookAvailability(BookAvailabilityEvent event) {
        if (!event.live()) {
            activeGenerations.computeIfPresent(event.sourceId(), (ignored, current) ->
                    event.generation() >= current ? null : current);
        }
    }

    public int activeBooks() {
        return activeGenerations.size();
    }
    public long acceptedBooks() {
        return acceptedBooks.get();
    }

    public long usableBooks() {
        return usableBooks.get();
    }
}

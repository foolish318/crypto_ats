package com.example.hft.datasource.deepbook.runtime;

import com.example.hft.datasource.engine.MarketDataListener;
import com.example.hft.datasource.normalizer.NormalizedMarketDataEvent;
import java.util.concurrent.atomic.AtomicLong;


public final class DeepBookStrategyListener implements MarketDataListener {
    private final AtomicLong acceptedBooks = new AtomicLong();
    private final AtomicLong usableBooks = new AtomicLong();

    @Override
    public void onMarketData(NormalizedMarketDataEvent event) {
        if (!(event instanceof AcceptedLocalBookEvent accepted)) {
            return;
        }
        acceptedBooks.incrementAndGet();
        DecimalBookLevel bid = accepted.book().bestBid();
        DecimalBookLevel ask = accepted.book().bestAsk();
        if (bid != null && ask != null && bid.price().compareTo(ask.price()) < 0) {
            usableBooks.incrementAndGet();
        }
    }

    public long acceptedBooks() {
        return acceptedBooks.get();
    }

    public long usableBooks() {
        return usableBooks.get();
    }
}

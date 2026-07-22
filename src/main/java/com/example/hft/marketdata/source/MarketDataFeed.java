package com.example.hft.marketdata.source;

import com.example.hft.marketdata.model.Quote;
import com.example.hft.marketdata.model.QuoteEvent;
import com.example.hft.marketdata.model.QuoteMessage;
import com.example.hft.marketdata.model.StopMessage;
import java.util.concurrent.BlockingQueue;
import java.util.List;



public final class MarketDataFeed implements Runnable {
    private final List<Quote> quotes;
    private final BlockingQueue<QuoteEvent> outbound;
    private final int workerCount;

    public MarketDataFeed(List<Quote> quotes) {
        this(quotes, null, 0);
    }

    private MarketDataFeed(List<Quote> quotes, BlockingQueue<QuoteEvent> outbound, int workerCount) {
        this.quotes = List.copyOf(quotes);
        this.outbound = outbound;
        this.workerCount = workerCount;
    }

    public MarketDataFeed connectTo(BlockingQueue<QuoteEvent> outbound, int workerCount) {
        return new MarketDataFeed(quotes, outbound, workerCount);
    }

    @Override
    public void run() {
        try {
            for (Quote quote : quotes) {
                outbound.put(new QuoteMessage(quote, System.nanoTime()));
            }
            for (int i = 0; i < workerCount; i++) {
                outbound.put(StopMessage.INSTANCE);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

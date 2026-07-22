package com.example.hft.pipeline;

import com.example.hft.marketdata.model.QuoteEvent;
import com.example.hft.marketdata.source.MarketDataFeed;
import com.example.hft.strategy.MarketDataProcessor;
import java.util.ArrayList;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.List;



public final class ConcurrentQuoteRunner {
    private static final int QUEUE_CAPACITY = 16;

    private final int workerCount;
    private final MarketDataFeed feed;
    private final MarketDataProcessor processor;

    public ConcurrentQuoteRunner(int workerCount, MarketDataFeed feed, MarketDataProcessor processor) {
        if (workerCount <= 0) {
            throw new IllegalArgumentException("worker count must be positive");
        }
        this.workerCount = workerCount;
        this.feed = feed;
        this.processor = processor;
    }

    public ProcessingStats run() throws InterruptedException {
        BlockingQueue<QuoteEvent> queue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
        ProcessingStats stats = new ProcessingStats();
        List<Thread> threads = new ArrayList<>();

        Thread feedThread = new Thread(feed.connectTo(queue, workerCount), "market-data-feed");
        threads.add(feedThread);

        for (int i = 0; i < workerCount; i++) {
            Thread worker = new Thread(new QuoteWorker(i + 1, queue, processor, stats), "quote-worker-" + (i + 1));
            threads.add(worker);
        }

        for (Thread thread : threads) {
            thread.start();
        }
        for (Thread thread : threads) {
            thread.join();
        }

        return stats;
    }
}

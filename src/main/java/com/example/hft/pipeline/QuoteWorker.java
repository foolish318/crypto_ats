package com.example.hft.pipeline;

import com.example.hft.marketdata.model.QuoteEvent;
import com.example.hft.marketdata.model.QuoteMessage;
import com.example.hft.marketdata.model.TradingSignal;
import com.example.hft.strategy.MarketDataProcessor;
import java.util.concurrent.BlockingQueue;



public final class QuoteWorker implements Runnable {
    private final BlockingQueue<QuoteEvent> inbound;
    private final MarketDataProcessor processor;
    private final ProcessingStats stats;

    public QuoteWorker(int workerId, BlockingQueue<QuoteEvent> inbound, MarketDataProcessor processor, ProcessingStats stats) {
        this.inbound = inbound;
        this.processor = processor;
        this.stats = stats;
    }

    @Override
    public void run() {
        try {
            while (true) {
                QuoteEvent event = inbound.take();
                if (event.isStop()) {
                    return;
                }

                QuoteMessage message = (QuoteMessage) event;
                TradingSignal signal = processor.signalFor(message.quote());
                stats.record(signal);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

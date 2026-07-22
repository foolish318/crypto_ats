package com.example.hft.pipeline;

import com.example.hft.benchmark.BenchmarkResult;
import com.example.hft.benchmark.WorkerMetrics;
import com.example.hft.marketdata.model.Quote;
import com.example.hft.marketdata.model.QuoteEvent;
import com.example.hft.marketdata.model.QuoteMessage;
import com.example.hft.marketdata.model.StopMessage;
import com.example.hft.marketdata.model.TradingSignal;
import com.example.hft.strategy.MarketDataProcessor;
import com.example.hft.strategy.QuoteValidator;
import com.example.hft.strategy.TradingDecisionEngine;
import java.util.ArrayList;
import java.util.List;
import org.jctools.queues.SpmcArrayQueue;




public final class JctoolsSpmcQueuePipeline implements QuotePipeline {
    private static final int QUEUE_CAPACITY = 4_096;

    @Override
    public String name() {
        return "v5-jctools-spmc";
    }

    @Override
    public BenchmarkResult run(List<Quote> quotes, int workerCount) throws InterruptedException {
        SpmcArrayQueue<QuoteEvent> queue = new SpmcArrayQueue<>(QUEUE_CAPACITY);
        WorkerMetrics[] metrics = new WorkerMetrics[workerCount];
        List<Thread> threads = new ArrayList<>();
        long[] producerWaitNanos = {0};

        for (int i = 0; i < workerCount; i++) {
            metrics[i] = new WorkerMetrics(quotes.size());
            int workerIndex = i;
            Thread worker = new Thread(() -> consume(queue, metrics[workerIndex]), "jctools-spmc-worker-" + (workerIndex + 1));
            threads.add(worker);
        }

        Thread producer = new Thread(() -> produce(quotes, queue, workerCount, producerWaitNanos), "jctools-spmc-producer");
        threads.add(producer);

        long runStart = System.nanoTime();
        for (Thread thread : threads) {
            thread.start();
        }
        for (Thread thread : threads) {
            thread.join();
        }
        long runEnd = System.nanoTime();

        return BenchmarkResult.of(name(), workerCount, quotes.size(), runEnd - runStart, producerWaitNanos[0], metrics);
    }

    private static void produce(List<Quote> quotes, SpmcArrayQueue<QuoteEvent> queue, int workerCount, long[] producerWaitNanos) {
        for (Quote quote : quotes) {
            long beforeOffer = System.nanoTime();
            offerSpinning(queue, new QuoteMessage(quote, beforeOffer));
            producerWaitNanos[0] += System.nanoTime() - beforeOffer;
        }
        for (int i = 0; i < workerCount; i++) {
            long beforeOffer = System.nanoTime();
            offerSpinning(queue, StopMessage.INSTANCE);
            producerWaitNanos[0] += System.nanoTime() - beforeOffer;
        }
    }

    private static void consume(SpmcArrayQueue<QuoteEvent> queue, WorkerMetrics metrics) {
        MarketDataProcessor processor = new MarketDataProcessor(new QuoteValidator(), new TradingDecisionEngine());
        while (true) {
            QuoteEvent event = pollSpinning(queue);
            if (event.isStop()) {
                return;
            }

            QuoteMessage message = (QuoteMessage) event;
            long processingStart = System.nanoTime();
            TradingSignal signal = processor.signalFor(message.quote(), metrics.moduleTiming());
            long processingEnd = System.nanoTime();
            metrics.record(signal, processingStart - message.enqueuedNanos(), processingEnd - processingStart, processingEnd - message.enqueuedNanos());
        }
    }

    private static void offerSpinning(SpmcArrayQueue<QuoteEvent> queue, QuoteEvent event) {
        while (!queue.offer(event)) {
            Thread.onSpinWait();
        }
    }

    private static QuoteEvent pollSpinning(SpmcArrayQueue<QuoteEvent> queue) {
        QuoteEvent event;
        while ((event = queue.poll()) == null) {
            Thread.onSpinWait();
        }
        return event;
    }
}

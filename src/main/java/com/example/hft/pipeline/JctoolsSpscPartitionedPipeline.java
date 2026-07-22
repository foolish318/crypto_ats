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
import org.jctools.queues.SpscArrayQueue;




public final class JctoolsSpscPartitionedPipeline implements QuotePipeline {
    private static final int QUEUE_CAPACITY = 4_096;

    @Override
    public String name() {
        return "v5-jctools-spsc-part";
    }

    @Override
    public BenchmarkResult run(List<Quote> quotes, int workerCount) throws InterruptedException {
        @SuppressWarnings("unchecked")
        SpscArrayQueue<QuoteEvent>[] queues = new SpscArrayQueue[workerCount];
        WorkerMetrics[] metrics = new WorkerMetrics[workerCount];
        List<Thread> threads = new ArrayList<>();
        long[] producerWaitNanos = {0};

        for (int i = 0; i < workerCount; i++) {
            queues[i] = new SpscArrayQueue<>(QUEUE_CAPACITY);
            metrics[i] = new WorkerMetrics(quotes.size());
            int workerIndex = i;
            Thread worker = new Thread(() -> consume(queues[workerIndex], metrics[workerIndex]), "jctools-spsc-worker-" + (workerIndex + 1));
            threads.add(worker);
        }

        Thread producer = new Thread(() -> producePartitioned(quotes, queues, producerWaitNanos), "jctools-spsc-producer");
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

    private static void producePartitioned(List<Quote> quotes, SpscArrayQueue<QuoteEvent>[] queues, long[] producerWaitNanos) {
        for (Quote quote : quotes) {
            int partition = Math.floorMod(quote.symbol().hashCode(), queues.length);
            long beforeOffer = System.nanoTime();
            offerSpinning(queues[partition], new QuoteMessage(quote, beforeOffer));
            producerWaitNanos[0] += System.nanoTime() - beforeOffer;
        }
        for (SpscArrayQueue<QuoteEvent> queue : queues) {
            long beforeOffer = System.nanoTime();
            offerSpinning(queue, StopMessage.INSTANCE);
            producerWaitNanos[0] += System.nanoTime() - beforeOffer;
        }
    }

    private static void consume(SpscArrayQueue<QuoteEvent> queue, WorkerMetrics metrics) {
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

    private static void offerSpinning(SpscArrayQueue<QuoteEvent> queue, QuoteEvent event) {
        while (!queue.offer(event)) {
            Thread.onSpinWait();
        }
    }

    private static QuoteEvent pollSpinning(SpscArrayQueue<QuoteEvent> queue) {
        QuoteEvent event;
        while ((event = queue.poll()) == null) {
            Thread.onSpinWait();
        }
        return event;
    }
}

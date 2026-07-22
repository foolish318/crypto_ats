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
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.List;



public final class PartitionedQueuePipeline implements QuotePipeline {
    private static final int QUEUE_CAPACITY = 4_096;

    @Override
    public String name() {
        return "v3-partitioned";
    }

    @Override
    public BenchmarkResult run(List<Quote> quotes, int workerCount) throws InterruptedException {
        @SuppressWarnings("unchecked")
        BlockingQueue<QuoteEvent>[] queues = new BlockingQueue[workerCount];
        WorkerMetrics[] metrics = new WorkerMetrics[workerCount];
        List<Thread> threads = new ArrayList<>();
        long[] producerWaitNanos = {0};

        for (int i = 0; i < workerCount; i++) {
            queues[i] = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
            metrics[i] = new WorkerMetrics(quotes.size());
            int workerIndex = i;
            Thread worker = new Thread(() -> consume(queues[workerIndex], metrics[workerIndex]), "partition-worker-" + (workerIndex + 1));
            threads.add(worker);
        }

        Thread producer = new Thread(() -> producePartitioned(quotes, queues, producerWaitNanos), "partition-producer");
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

    private static void producePartitioned(List<Quote> quotes, BlockingQueue<QuoteEvent>[] queues, long[] producerWaitNanos) {
        try {
            for (Quote quote : quotes) {
                int partition = Math.floorMod(quote.symbol().hashCode(), queues.length);
                long beforePut = System.nanoTime();
                queues[partition].put(new QuoteMessage(quote, beforePut));
                producerWaitNanos[0] += System.nanoTime() - beforePut;
            }
            for (BlockingQueue<QuoteEvent> queue : queues) {
                queue.put(StopMessage.INSTANCE);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void consume(BlockingQueue<QuoteEvent> queue, WorkerMetrics metrics) {
        MarketDataProcessor processor = new MarketDataProcessor(new QuoteValidator(), new TradingDecisionEngine());
        try {
            while (true) {
                QuoteEvent event = queue.take();
                if (event.isStop()) {
                    return;
                }
                QuoteMessage message = (QuoteMessage) event;
                long processingStart = System.nanoTime();
                TradingSignal signal = processor.signalFor(message.quote(), metrics.moduleTiming());
                long processingEnd = System.nanoTime();
                metrics.record(signal, processingStart - message.enqueuedNanos(), processingEnd - processingStart, processingEnd - message.enqueuedNanos());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

package com.example.hft.pipeline;

import com.example.hft.marketdata.model.DepthLatencyEnvelope;
import com.example.hft.marketdata.model.DepthMarketDataRecord;
import com.example.hft.marketdata.model.TradingSignal;
import com.example.hft.strategy.DepthMarketDataProcessor;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.List;
import org.jctools.queues.SpscArrayQueue;




public final class SpscDepthPipeline implements DepthPipeline {
    private static final int QUEUE_CAPACITY = 8_192;

    private final SpscArrayQueue<DepthLatencyEnvelope>[] queues;
    private final List<Thread> threads = new ArrayList<>();
    private final DepthLatencyStats stats;
    private final AtomicReference<Throwable> failure = new AtomicReference<>();

    @SuppressWarnings("unchecked")
    public SpscDepthPipeline(int workers, int capacity) {
        this.queues = new SpscArrayQueue[workers];
        this.stats = new DepthLatencyStats("v5-depth-spsc", capacity);
        for (int i = 0; i < workers; i++) {
            queues[i] = new SpscArrayQueue<>(QUEUE_CAPACITY);
        }
    }

    @Override
    public String name() {
        return "v5-depth-spsc";
    }

    @Override
    public DepthLatencyStats stats() {
        return stats;
    }

    @Override
    public void start() {
        for (int i = 0; i < queues.length; i++) {
            int workerIndex = i;
            Thread worker = new Thread(() -> runWorker(queues[workerIndex]), name() + "-worker-" + (workerIndex + 1));
            threads.add(worker);
            worker.start();
        }
    }

    @Override
    public void publish(DepthMarketDataRecord record, long enqueuedNanos) {
        int partition = Math.floorMod(record.symbol().hashCode(), queues.length);
        offerSpinning(queues[partition], DepthLatencyEnvelope.record(record, enqueuedNanos));
    }

    @Override
    public void stopAndJoin() throws InterruptedException {
        for (SpscArrayQueue<DepthLatencyEnvelope> queue : queues) {
            offerSpinning(queue, DepthLatencyEnvelope.stop());
        }
        for (Thread thread : threads) {
            thread.join();
        }
        if (failure.get() != null) {
            throw new IllegalStateException(name() + " worker failed", failure.get());
        }
    }

    private void runWorker(SpscArrayQueue<DepthLatencyEnvelope> queue) {
        DepthMarketDataProcessor processor = new DepthMarketDataProcessor();
        try {
            while (true) {
                DepthLatencyEnvelope envelope = pollSpinning(queue);
                if (envelope.isStop()) {
                    return;
                }
                long processingStart = System.nanoTime();
                TradingSignal signal = processor.signalFor(envelope.record());
                long processingEnd = System.nanoTime();
                stats.record(envelope, processingStart, processingEnd, signal);
            }
        } catch (Throwable t) {
            failure.compareAndSet(null, t);
        }
    }

    private static void offerSpinning(SpscArrayQueue<DepthLatencyEnvelope> queue, DepthLatencyEnvelope envelope) {
        while (!queue.offer(envelope)) {
            Thread.onSpinWait();
        }
    }

    private static DepthLatencyEnvelope pollSpinning(SpscArrayQueue<DepthLatencyEnvelope> queue) {
        DepthLatencyEnvelope envelope;
        while ((envelope = queue.poll()) == null) {
            Thread.onSpinWait();
        }
        return envelope;
    }
}

package com.example.hft.pipeline;

import com.example.hft.marketdata.model.DepthLatencyEnvelope;
import com.example.hft.marketdata.model.DepthMarketDataRecord;
import com.example.hft.marketdata.model.TradingSignal;
import com.example.hft.strategy.DepthMarketDataProcessor;
import com.lmax.disruptor.BusySpinWaitStrategy;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.RingBuffer;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadFactory;
import java.util.List;




public final class DisruptorDepthPipeline implements DepthPipeline {
    private static final int RING_BUFFER_SIZE = 8_192;

    private final List<Disruptor<DisruptorDepthEvent>> disruptors = new ArrayList<>();
    private final List<RingBuffer<DisruptorDepthEvent>> ringBuffers = new ArrayList<>();
    private final CountDownLatch stopLatch;
    private final DepthLatencyStats stats;
    private final AtomicInteger threadId = new AtomicInteger();

    public DisruptorDepthPipeline(int workers, int capacity) {
        this.stopLatch = new CountDownLatch(workers);
        this.stats = new DepthLatencyStats("v10-depth-disruptor", capacity);
        for (int i = 0; i < workers; i++) {
            disruptors.add(createDisruptor(i + 1));
        }
    }

    @Override
    public String name() {
        return "v10-depth-disruptor";
    }

    @Override
    public DepthLatencyStats stats() {
        return stats;
    }

    @Override
    public void start() {
        for (Disruptor<DisruptorDepthEvent> disruptor : disruptors) {
            ringBuffers.add(disruptor.start());
        }
    }

    @Override
    public void publish(DepthMarketDataRecord record, long enqueuedNanos) {
        int partition = Math.floorMod(record.symbol().hashCode(), ringBuffers.size());
        publish(ringBuffers.get(partition), record, enqueuedNanos, false);
    }

    @Override
    public void stopAndJoin() throws InterruptedException {
        for (RingBuffer<DisruptorDepthEvent> ringBuffer : ringBuffers) {
            publish(ringBuffer, null, 0, true);
        }
        stopLatch.await();
        for (Disruptor<DisruptorDepthEvent> disruptor : disruptors) {
            disruptor.shutdown();
        }
    }

    private Disruptor<DisruptorDepthEvent> createDisruptor(int partition) {
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable, name() + "-worker-" + partition + "-" + threadId.incrementAndGet());
            thread.setDaemon(false);
            return thread;
        };
        Disruptor<DisruptorDepthEvent> disruptor = new Disruptor<>(
                DisruptorDepthEvent::new,
                RING_BUFFER_SIZE,
                threadFactory,
                ProducerType.SINGLE,
                new BusySpinWaitStrategy()
        );
        disruptor.handleEventsWith(handler());
        return disruptor;
    }

    private EventHandler<DisruptorDepthEvent> handler() {
        DepthMarketDataProcessor processor = new DepthMarketDataProcessor();
        return (event, sequence, endOfBatch) -> {
            if (event.isStop()) {
                stopLatch.countDown();
                return;
            }
            long processingStart = System.nanoTime();
            TradingSignal signal = processor.signalFor(event.record());
            long processingEnd = System.nanoTime();
            stats.record(DepthLatencyEnvelope.record(event.record(), event.enqueuedNanos()), processingStart, processingEnd, signal);
        };
    }

    private static void publish(RingBuffer<DisruptorDepthEvent> ringBuffer, DepthMarketDataRecord record,
                                long enqueuedNanos, boolean stop) {
        long sequence = ringBuffer.next();
        try {
            DisruptorDepthEvent event = ringBuffer.get(sequence);
            event.set(record, enqueuedNanos, stop);
        } finally {
            ringBuffer.publish(sequence);
        }
    }
}

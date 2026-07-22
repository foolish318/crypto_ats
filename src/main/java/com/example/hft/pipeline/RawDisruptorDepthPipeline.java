package com.example.hft.pipeline;

import com.example.hft.exchange.binance.BinanceDepthParser;
import com.example.hft.marketdata.model.DepthBookTop;
import com.example.hft.marketdata.model.DepthMarketDataRecord;
import com.example.hft.marketdata.model.DepthUpdate;
import com.example.hft.marketdata.model.LocalOrderBook;
import com.example.hft.marketdata.model.RawDepthPayload;
import com.example.hft.strategy.DepthMarketDataProcessor;
import com.lmax.disruptor.BusySpinWaitStrategy;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.RingBuffer;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadFactory;
import java.util.List;
import java.util.Map;




public final class RawDisruptorDepthPipeline {
    private static final int RING_BUFFER_SIZE = 8_192;
    private static final int TOP_LEVELS = 10;

    private final Map<String, LocalOrderBook> books;
    private final List<Disruptor<RawDepthDisruptorEvent>> disruptors = new ArrayList<>();
    private final List<RingBuffer<RawDepthDisruptorEvent>> ringBuffers = new ArrayList<>();
    private final CountDownLatch stopLatch;
    private final RawDepthLatencyStats stats;
    private final AtomicReference<Throwable> failure = new AtomicReference<>();
    private final AtomicInteger threadId = new AtomicInteger();

    public RawDisruptorDepthPipeline(Map<String, LocalOrderBook> books, int partitions, int capacity) {
        this.books = books;
        this.stopLatch = new CountDownLatch(partitions);
        this.stats = new RawDepthLatencyStats("v11-raw-disruptor", capacity);
        for (int i = 0; i < partitions; i++) {
            disruptors.add(createDisruptor(i + 1));
        }
    }

    public RawDepthLatencyStats stats() {
        return stats;
    }

    public void start() {
        for (Disruptor<RawDepthDisruptorEvent> disruptor : disruptors) {
            ringBuffers.add(disruptor.start());
        }
    }

    public void publish(RawDepthPayload payload, long publishedNanos) {
        int partition = partitionFor(payload.routingSymbol());
        RingBuffer<RawDepthDisruptorEvent> ringBuffer = ringBuffers.get(partition);
        long sequence = ringBuffer.next();
        try {
            ringBuffer.get(sequence).setData(payload, publishedNanos);
        } finally {
            ringBuffer.publish(sequence);
        }
    }

    public void stopAndJoin() throws InterruptedException {
        for (RingBuffer<RawDepthDisruptorEvent> ringBuffer : ringBuffers) {
            long sequence = ringBuffer.next();
            try {
                ringBuffer.get(sequence).setStop();
            } finally {
                ringBuffer.publish(sequence);
            }
        }
        stopLatch.await();
        for (Disruptor<RawDepthDisruptorEvent> disruptor : disruptors) {
            disruptor.shutdown();
        }
        if (failure.get() != null) {
            throw new IllegalStateException("raw disruptor pipeline failed", failure.get());
        }
    }

    private int partitionFor(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return 0;
        }
        return Math.floorMod(symbol.hashCode(), ringBuffers.size());
    }

    private Disruptor<RawDepthDisruptorEvent> createDisruptor(int partition) {
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable, "v11-raw-disruptor-p" + partition + "-" + threadId.incrementAndGet());
            thread.setDaemon(false);
            return thread;
        };
        Disruptor<RawDepthDisruptorEvent> disruptor = new Disruptor<>(
                RawDepthDisruptorEvent::new,
                RING_BUFFER_SIZE,
                threadFactory,
                ProducerType.SINGLE,
                new BusySpinWaitStrategy()
        );
        disruptor.handleEventsWith(parseHandler()).then(bookHandler()).then(decisionHandler());
        return disruptor;
    }

    private EventHandler<RawDepthDisruptorEvent> parseHandler() {
        BinanceDepthParser parser = new BinanceDepthParser();
        return (event, sequence, endOfBatch) -> {
            if (event.isStop()) {
                return;
            }
            try {
                event.setParseStartNanos(System.nanoTime());
                event.setUpdate(parser.parseUpdate(event.rawPayload()));
                event.setParsedNanos(System.nanoTime());
            } catch (Throwable t) {
                event.drop();
                failure.compareAndSet(null, t);
            }
        };
    }

    private EventHandler<RawDepthDisruptorEvent> bookHandler() {
        return (event, sequence, endOfBatch) -> {
            if (event.isStop() || event.isDropped()) {
                return;
            }
            try {
                event.setBookStartNanos(System.nanoTime());
                DepthUpdate update = event.update();
                LocalOrderBook book = books.get(update.symbol());
                if (book == null || !book.applyDepthUpdate(update)) {
                    event.drop();
                    return;
                }

                DepthBookTop top = book.topLevels(TOP_LEVELS);
                event.setBookUpdatedNanos(System.nanoTime());
                event.setRecord(new DepthMarketDataRecord(event.sequenceNumber(), update.symbol(),
                        update.exchangeEventTimeMillis(), event.localReceivedEpochMillis(), event.rawReceivedNanos(),
                        event.parsedNanos(), event.bookUpdatedNanos(), top.bidPrices(), top.bidSizes(),
                        top.askPrices(), top.askSizes(), event.rawPayload()));
            } catch (Throwable t) {
                event.drop();
                failure.compareAndSet(null, t);
            }
        };
    }

    private EventHandler<RawDepthDisruptorEvent> decisionHandler() {
        DepthMarketDataProcessor processor = new DepthMarketDataProcessor();
        return (event, sequence, endOfBatch) -> {
            if (event.isStop()) {
                stopLatch.countDown();
                return;
            }
            if (event.isDropped()) {
                stats.addDropped();
                return;
            }
            try {
                event.setProcessingStartNanos(System.nanoTime());
                event.setSignal(processor.signalFor(event.record()));
                event.setProcessingEndNanos(System.nanoTime());
                stats.record(event);
            } catch (Throwable t) {
                stats.addDropped();
                failure.compareAndSet(null, t);
            }
        };
    }
}

package com.example.hft.pipeline;

import com.example.hft.marketdata.model.DepthMarketDataRecord;
import java.util.Arrays;



public final class RawDepthLatencyStats {
    private final String name;
    private final long[] exchangeToLocalReceived;
    private final long[] exchangeToProcessorDone;
    private final long[] queueLatencies;
    private final long[] parseLatencies;
    private final long[] bookLatencies;
    private final long[] processorLatencies;
    private final long[] localE2eLatencies;

    private long processed;
    private long dropped;
    private long producerOfferNanos;
    private long buyPressure;
    private long sellPressure;
    private long neutral;
    private long doNotTrade;

    public RawDepthLatencyStats(String name, int capacity) {
        this.name = name;
        this.exchangeToLocalReceived = new long[capacity];
        this.exchangeToProcessorDone = new long[capacity];
        this.queueLatencies = new long[capacity];
        this.parseLatencies = new long[capacity];
        this.bookLatencies = new long[capacity];
        this.processorLatencies = new long[capacity];
        this.localE2eLatencies = new long[capacity];
    }

    public synchronized void addProducerOfferNanos(long nanos) {
        producerOfferNanos += nanos;
    }

    public synchronized void addDropped() {
        dropped++;
    }

    public synchronized void record(RawDepthDisruptorEvent event) {
        int index = (int) processed;
        DepthMarketDataRecord record = event.record();
        long eventEpochNanos = record.exchangeEventTimeMillis() * 1_000_000L;
        long receivedEpochNanos = record.localReceivedEpochMillis() * 1_000_000L;
        long doneEpochNanos = receivedEpochNanos + (event.processingEndNanos() - record.rawReceivedNanos());

        exchangeToLocalReceived[index] = receivedEpochNanos - eventEpochNanos;
        exchangeToProcessorDone[index] = doneEpochNanos - eventEpochNanos;
        queueLatencies[index] = event.parseStartNanos() - event.publishedNanos();
        parseLatencies[index] = event.parsedNanos() - event.parseStartNanos();
        bookLatencies[index] = event.bookUpdatedNanos() - event.bookStartNanos();
        processorLatencies[index] = event.processingEndNanos() - event.processingStartNanos();
        localE2eLatencies[index] = event.processingEndNanos() - record.rawReceivedNanos();
        processed++;

        switch (event.signal()) {
            case BUY_PRESSURE -> buyPressure++;
            case SELL_PRESSURE -> sellPressure++;
            case NEUTRAL -> neutral++;
            case DO_NOT_TRADE -> doNotTrade++;
        }
    }

    public synchronized long processed() {
        return processed;
    }

    public synchronized long dropped() {
        return dropped;
    }

    public synchronized String toDisplayLine() {
        return name
                + " processed=" + processed
                + " dropped=" + dropped
                + " exchToRecvAvg=" + micros(avg(exchangeToLocalReceived)) + "us"
                + " exchToRecvP99=" + micros(percentile(exchangeToLocalReceived, 0.99)) + "us"
                + " exchToDoneAvg=" + micros(avg(exchangeToProcessorDone)) + "us"
                + " exchToDoneP99=" + micros(percentile(exchangeToProcessorDone, 0.99)) + "us"
                + " queueAvg=" + micros(avg(queueLatencies)) + "us"
                + " queueP99=" + micros(percentile(queueLatencies, 0.99)) + "us"
                + " parseAvg=" + micros(avg(parseLatencies)) + "us"
                + " parseP99=" + micros(percentile(parseLatencies, 0.99)) + "us"
                + " bookAvg=" + micros(avg(bookLatencies)) + "us"
                + " bookP99=" + micros(percentile(bookLatencies, 0.99)) + "us"
                + " processorAvg=" + micros(avg(processorLatencies)) + "us"
                + " processorP99=" + micros(percentile(processorLatencies, 0.99)) + "us"
                + " localE2EAvg=" + micros(avg(localE2eLatencies)) + "us"
                + " localE2EP99=" + micros(percentile(localE2eLatencies, 0.99)) + "us"
                + " producerOffer=" + millis(producerOfferNanos) + "ms"
                + " signals[B=" + buyPressure + " S=" + sellPressure + " N=" + neutral + " X=" + doNotTrade + "]";
    }

    private double avg(long[] values) {
        if (processed == 0) {
            return 0.0;
        }
        long total = 0;
        for (int i = 0; i < processed; i++) {
            total += values[i];
        }
        return (double) total / processed;
    }

    private long percentile(long[] values, double percentile) {
        if (processed == 0) {
            return 0;
        }
        long[] copy = Arrays.copyOf(values, (int) processed);
        Arrays.sort(copy);
        int index = (int) Math.ceil(copy.length * percentile) - 1;
        return copy[Math.max(0, Math.min(index, copy.length - 1))];
    }

    private static String micros(double nanos) {
        return String.format("%.2f", nanos / 1_000.0);
    }

    private static String millis(long nanos) {
        return String.format("%.2f", nanos / 1_000_000.0);
    }
}

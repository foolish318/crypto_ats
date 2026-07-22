package com.example.hft.pipeline;

import com.example.hft.marketdata.model.LiveQuoteEnvelope;
import com.example.hft.marketdata.model.TradingSignal;
import java.util.Arrays;



public final class LiveLatencyStats {
    private final long[] parseLatencies;
    private final long[] queueLatencies;
    private final long[] processorLatencies;
    private final long[] localE2eLatencies;

    private long processed;
    private long producerOfferNanos;
    private long buyPressure;
    private long sellPressure;
    private long neutral;
    private long doNotTrade;

    public LiveLatencyStats(int capacity) {
        this.parseLatencies = new long[capacity];
        this.queueLatencies = new long[capacity];
        this.processorLatencies = new long[capacity];
        this.localE2eLatencies = new long[capacity];
    }

    public void addProducerOfferNanos(long nanos) {
        producerOfferNanos += nanos;
    }

    public void record(LiveQuoteEnvelope envelope, long processingStartNanos, long processingEndNanos, TradingSignal signal) {
        int index = (int) processed;
        parseLatencies[index] = envelope.parsedNanos() - envelope.rawReceivedNanos();
        queueLatencies[index] = processingStartNanos - envelope.enqueuedNanos();
        processorLatencies[index] = processingEndNanos - processingStartNanos;
        localE2eLatencies[index] = processingEndNanos - envelope.rawReceivedNanos();
        processed++;

        switch (signal) {
            case BUY_PRESSURE -> buyPressure++;
            case SELL_PRESSURE -> sellPressure++;
            case NEUTRAL -> neutral++;
            case DO_NOT_TRADE -> doNotTrade++;
        }
    }

    public long processed() {
        return processed;
    }

    public String toDisplayLine() {
        return "live-latency processed=" + processed
                + " parseAvg=" + micros(avg(parseLatencies)) + "us"
                + " parseP99=" + micros(percentile(parseLatencies, 0.99)) + "us"
                + " queueAvg=" + micros(avg(queueLatencies)) + "us"
                + " queueP99=" + micros(percentile(queueLatencies, 0.99)) + "us"
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

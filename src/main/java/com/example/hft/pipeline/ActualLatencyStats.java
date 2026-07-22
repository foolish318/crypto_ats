package com.example.hft.pipeline;

import com.example.hft.marketdata.model.ActualLatencyEnvelope;
import com.example.hft.marketdata.model.ActualMarketDataRecord;
import com.example.hft.marketdata.model.TradingSignal;
import java.util.Arrays;



public final class ActualLatencyStats {
    private final String name;
    private final long[] exchangeToLocalReceived;
    private final long[] exchangeToProcessorDone;
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

    public ActualLatencyStats(String name, int capacity) {
        this.name = name;
        this.exchangeToLocalReceived = new long[capacity];
        this.exchangeToProcessorDone = new long[capacity];
        this.parseLatencies = new long[capacity];
        this.queueLatencies = new long[capacity];
        this.processorLatencies = new long[capacity];
        this.localE2eLatencies = new long[capacity];
    }

    public synchronized void addProducerOfferNanos(long nanos) {
        producerOfferNanos += nanos;
    }

    public synchronized void record(ActualLatencyEnvelope envelope, long processingStartNanos, long processingEndNanos,
                                    TradingSignal signal) {
        ActualMarketDataRecord record = envelope.record();
        int index = (int) processed;
        long eventEpochNanos = record.exchangeEventTimeMillis() * 1_000_000L;
        long receivedEpochNanos = record.localReceivedEpochMillis() * 1_000_000L;
        long doneEpochNanos = receivedEpochNanos + (processingEndNanos - record.rawReceivedNanos());

        exchangeToLocalReceived[index] = receivedEpochNanos - eventEpochNanos;
        exchangeToProcessorDone[index] = doneEpochNanos - eventEpochNanos;
        parseLatencies[index] = record.parsedNanos() - record.rawReceivedNanos();
        queueLatencies[index] = processingStartNanos - envelope.enqueuedNanos();
        processorLatencies[index] = processingEndNanos - processingStartNanos;
        localE2eLatencies[index] = processingEndNanos - record.rawReceivedNanos();
        processed++;

        switch (signal) {
            case BUY_PRESSURE -> buyPressure++;
            case SELL_PRESSURE -> sellPressure++;
            case NEUTRAL -> neutral++;
            case DO_NOT_TRADE -> doNotTrade++;
        }
    }

    public synchronized long processed() {
        return processed;
    }

    public synchronized double avgProcessorMicros() {
        return avg(processorLatencies) / 1_000.0;
    }

    public synchronized String toDisplayLine() {
        return name
                + " processed=" + processed
                + " exchToRecvAvg=" + micros(avg(exchangeToLocalReceived)) + "us"
                + " exchToRecvP99=" + micros(percentile(exchangeToLocalReceived, 0.99)) + "us"
                + " exchToDoneAvg=" + micros(avg(exchangeToProcessorDone)) + "us"
                + " exchToDoneP99=" + micros(percentile(exchangeToProcessorDone, 0.99)) + "us"
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

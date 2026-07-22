package com.example.hft.pipeline;

import com.example.hft.marketdata.model.DepthMarketDataRecord;
import com.example.hft.marketdata.model.DepthUpdate;
import com.example.hft.marketdata.model.RawDepthPayload;
import com.example.hft.marketdata.model.TradingSignal;


public final class RawDepthDisruptorEvent {
    private long sequenceNumber;
    private String routingSymbol;
    private String rawPayload;
    private long localReceivedEpochMillis;
    private long rawReceivedNanos;
    private long publishedNanos;
    private long parseStartNanos;
    private long parsedNanos;
    private long bookStartNanos;
    private long bookUpdatedNanos;
    private long processingStartNanos;
    private long processingEndNanos;
    private DepthUpdate update;
    private DepthMarketDataRecord record;
    private TradingSignal signal;
    private boolean stop;
    private boolean dropped;

    public void setData(RawDepthPayload payload, long publishedNanos) {
        this.sequenceNumber = payload.sequenceNumber();
        this.routingSymbol = payload.routingSymbol();
        this.rawPayload = payload.rawPayload();
        this.localReceivedEpochMillis = payload.localReceivedEpochMillis();
        this.rawReceivedNanos = payload.rawReceivedNanos();
        this.publishedNanos = publishedNanos;
        this.parseStartNanos = 0;
        this.parsedNanos = 0;
        this.bookStartNanos = 0;
        this.bookUpdatedNanos = 0;
        this.processingStartNanos = 0;
        this.processingEndNanos = 0;
        this.update = null;
        this.record = null;
        this.signal = null;
        this.stop = false;
        this.dropped = false;
    }

    public void setStop() {
        this.stop = true;
        this.dropped = false;
        this.rawPayload = null;
        this.update = null;
        this.record = null;
        this.signal = null;
    }

    public long sequenceNumber() {
        return sequenceNumber;
    }

    public String routingSymbol() {
        return routingSymbol;
    }

    public String rawPayload() {
        return rawPayload;
    }

    public long localReceivedEpochMillis() {
        return localReceivedEpochMillis;
    }

    public long rawReceivedNanos() {
        return rawReceivedNanos;
    }

    public long publishedNanos() {
        return publishedNanos;
    }

    public long parseStartNanos() {
        return parseStartNanos;
    }

    public void setParseStartNanos(long parseStartNanos) {
        this.parseStartNanos = parseStartNanos;
    }

    public long parsedNanos() {
        return parsedNanos;
    }

    public void setParsedNanos(long parsedNanos) {
        this.parsedNanos = parsedNanos;
    }

    public long bookStartNanos() {
        return bookStartNanos;
    }

    public void setBookStartNanos(long bookStartNanos) {
        this.bookStartNanos = bookStartNanos;
    }

    public long bookUpdatedNanos() {
        return bookUpdatedNanos;
    }

    public void setBookUpdatedNanos(long bookUpdatedNanos) {
        this.bookUpdatedNanos = bookUpdatedNanos;
    }

    public long processingStartNanos() {
        return processingStartNanos;
    }

    public void setProcessingStartNanos(long processingStartNanos) {
        this.processingStartNanos = processingStartNanos;
    }

    public long processingEndNanos() {
        return processingEndNanos;
    }

    public void setProcessingEndNanos(long processingEndNanos) {
        this.processingEndNanos = processingEndNanos;
    }

    public DepthUpdate update() {
        return update;
    }

    public void setUpdate(DepthUpdate update) {
        this.update = update;
    }

    public DepthMarketDataRecord record() {
        return record;
    }

    public void setRecord(DepthMarketDataRecord record) {
        this.record = record;
    }

    public TradingSignal signal() {
        return signal;
    }

    public void setSignal(TradingSignal signal) {
        this.signal = signal;
    }

    public boolean isStop() {
        return stop;
    }

    public boolean isDropped() {
        return dropped;
    }

    public void drop() {
        this.dropped = true;
    }
}

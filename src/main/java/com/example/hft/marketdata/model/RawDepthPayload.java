package com.example.hft.marketdata.model;


public final class RawDepthPayload {
    private final long sequenceNumber;
    private final String routingSymbol;
    private final long localReceivedEpochMillis;
    private final long rawReceivedNanos;
    private final String rawPayload;

    public RawDepthPayload(long sequenceNumber, String routingSymbol, long localReceivedEpochMillis,
                           long rawReceivedNanos, String rawPayload) {
        this.sequenceNumber = sequenceNumber;
        this.routingSymbol = routingSymbol;
        this.localReceivedEpochMillis = localReceivedEpochMillis;
        this.rawReceivedNanos = rawReceivedNanos;
        this.rawPayload = rawPayload;
    }

    public long sequenceNumber() {
        return sequenceNumber;
    }

    public String routingSymbol() {
        return routingSymbol;
    }

    public long localReceivedEpochMillis() {
        return localReceivedEpochMillis;
    }

    public long rawReceivedNanos() {
        return rawReceivedNanos;
    }

    public String rawPayload() {
        return rawPayload;
    }
}

package com.example.hft.datasource.transport;

import java.util.Arrays;


public record RawInboundMessage(
        String source,
        String exchange,
        String symbol,
        TransportType transport,
        String channel,
        long receivedNanos,
        long exchangeTimeMillis,
        long sequence,
        byte[] payload
) {
    public RawInboundMessage {
        payload = Arrays.copyOf(payload, payload.length);
    }

    @Override
    public byte[] payload() {
        return Arrays.copyOf(payload, payload.length);
    }
}

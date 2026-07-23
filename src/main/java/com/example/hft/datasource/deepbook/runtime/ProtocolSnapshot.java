package com.example.hft.datasource.deepbook.runtime;


public record ProtocolSnapshot(
        long controlMessages,
        long subscriptionAcks,
        long subscriptionErrors,
        long heartbeats,
        long pingsSent,
        long pongsReceived,
        long protocolErrors,
        boolean subscriptionAcknowledged,
        boolean awaitingPong
) {
}
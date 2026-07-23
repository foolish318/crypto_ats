package com.example.hft.datasource.deepbook;

import java.net.URI;


public record DeepBookSourceDefinition(
        String id,
        String exchange,
        String symbol,
        String channel,
        int depthLevels,
        URI webSocketUri,
        String subscribeMessage,
        URI snapshotUri,
        boolean requiresAuthentication,
        String note
) {
    public boolean hasSnapshotUri() {
        return snapshotUri != null;
    }

    public boolean hasSubscribeMessage() {
        return subscribeMessage != null && !subscribeMessage.isBlank();
    }
}
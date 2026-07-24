package com.example.hft.marketdata.trade;

import com.example.hft.marketdata.model.Venue;
import java.net.URI;

public record PublicTradeSourceDefinition(
        String id,
        Venue venue,
        String venueSymbol,
        URI webSocketUri,
        String subscribeMessage
) {
    public PublicTradeSourceDefinition {
        if (id == null || id.isBlank() || venue == null
                || venueSymbol == null || venueSymbol.isBlank() || webSocketUri == null) {
            throw new IllegalArgumentException("trade source identity and URI are required");
        }
        subscribeMessage = subscribeMessage == null ? "" : subscribeMessage;
    }

    public boolean hasSubscribeMessage() {
        return !subscribeMessage.isBlank();
    }
}
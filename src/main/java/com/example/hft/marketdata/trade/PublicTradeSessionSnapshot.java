package com.example.hft.marketdata.trade;

import com.example.hft.datasource.deepbook.runtime.RecoverySnapshot;
import com.example.hft.marketdata.model.Venue;

public record PublicTradeSessionSnapshot(
        String sourceId,
        Venue venue,
        String venueSymbol,
        long streamEpoch,
        long rawMessages,
        long normalizedTrades,
        long publishedTrades,
        long duplicateTrades,
        long outOfOrderTrades,
        long invalidMessages,
        RecoverySnapshot recovery,
        String lastFailure
) {
}
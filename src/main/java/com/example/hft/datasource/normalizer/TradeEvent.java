package com.example.hft.datasource.normalizer;

import com.example.hft.datasource.transport.TransportType;
import java.math.BigDecimal;


public record TradeEvent(
        String source,
        String exchange,
        String symbol,
        TransportType transport,
        long receivedNanos,
        long exchangeTimeMillis,
        long sequence,
        String tradeId,
        BigDecimal price,
        BigDecimal size,
        boolean buyerIsMaker
) implements NormalizedMarketDataEvent {
}

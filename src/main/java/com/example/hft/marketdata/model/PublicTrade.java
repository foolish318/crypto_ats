package com.example.hft.marketdata.model;

import com.example.hft.datasource.normalizer.NormalizedMarketDataEvent;
import com.example.hft.datasource.transport.TransportType;
import java.math.BigDecimal;

public record PublicTrade(
        String source,
        MarketEventHeader header,
        String tradeId,
        BigDecimal price,
        BigDecimal quantity,
        AggressorSide aggressorSide
) implements NormalizedMarketDataEvent {
    public PublicTrade {
        if (source == null || source.isBlank() || header == null
                || tradeId == null || tradeId.isBlank()
                || price == null || price.signum() <= 0
                || quantity == null || quantity.signum() <= 0
                || aggressorSide == null) {
            throw new IllegalArgumentException("valid public trade fields are required");
        }
    }

    @Override
    public String exchange() {
        return header.venue().name();
    }

    @Override
    public String symbol() {
        return header.venueSymbol();
    }

    @Override
    public TransportType transport() {
        return TransportType.WEBSOCKET;
    }

    @Override
    public long receivedNanos() {
        return header.receiveMonotonicNanos();
    }

    @Override
    public long exchangeTimeMillis() {
        return header.exchangeEpochMillis();
    }

    @Override
    public long sequence() {
        return header.localSequence();
    }
}
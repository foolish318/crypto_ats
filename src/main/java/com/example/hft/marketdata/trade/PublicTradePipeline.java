package com.example.hft.marketdata.trade;

import com.example.hft.datasource.engine.MarketDataEngine;
import com.example.hft.marketdata.model.PublicTrade;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public final class PublicTradePipeline {
    private static final int MAX_DEDUPLICATION_IDS = 100_000;

    private final PublicTradeNormalizer normalizer;
    private final MarketDataEngine engine;
    private final Map<String, Boolean> recentTradeIds = new LinkedHashMap<>(16, 0.75f, true);
    private final AtomicLong localSequence = new AtomicLong();
    private long lastExchangeTimestamp;
    private long normalized;
    private long published;
    private long duplicates;
    private long outOfOrder;
    private long invalid;

    public PublicTradePipeline(PublicTradeNormalizer normalizer, MarketDataEngine engine) {
        if (normalizer == null || engine == null) {
            throw new IllegalArgumentException("normalizer and engine are required");
        }
        this.normalizer = normalizer;
        this.engine = engine;
    }

    public synchronized TradeProcessingResult onMessage(
            String payload,
            long streamEpoch,
            long receiveEpochMillis,
            long receiveNanos
    ) {
        try {
            List<PublicTrade> events = normalizer.normalize(
                    payload,
                    streamEpoch,
                    receiveEpochMillis,
                    receiveNanos,
                    localSequence::incrementAndGet
            );
            int messagePublished = 0;
            int messageDuplicates = 0;
            int messageOutOfOrder = 0;
            for (PublicTrade trade : events) {
                normalized++;
                if (recentTradeIds.containsKey(trade.tradeId())) {
                    duplicates++;
                    messageDuplicates++;
                    continue;
                }
                recentTradeIds.put(trade.tradeId(), Boolean.TRUE);
                trimDeduplicationWindow();
                if (trade.header().exchangeEpochMillis() < lastExchangeTimestamp) {
                    outOfOrder++;
                    messageOutOfOrder++;
                }
                lastExchangeTimestamp = Math.max(
                        lastExchangeTimestamp,
                        trade.header().exchangeEpochMillis()
                );
                if (engine.publishTrade(trade).published()) {
                    published++;
                    messagePublished++;
                }
            }
            return new TradeProcessingResult(
                    events.size(), messagePublished, messageDuplicates,
                    messageOutOfOrder, 0, ""
            );
        } catch (Exception error) {
            invalid++;
            return new TradeProcessingResult(
                    0, 0, 0, 0, 1,
                    error.getClass().getSimpleName() + ": " + error.getMessage()
            );
        }
    }

    public synchronized long normalized() {
        return normalized;
    }

    public synchronized long published() {
        return published;
    }

    public synchronized long duplicates() {
        return duplicates;
    }

    public synchronized long outOfOrder() {
        return outOfOrder;
    }

    public synchronized long invalid() {
        return invalid;
    }

    private void trimDeduplicationWindow() {
        while (recentTradeIds.size() > MAX_DEDUPLICATION_IDS) {
            String oldest = recentTradeIds.keySet().iterator().next();
            recentTradeIds.remove(oldest);
        }
    }
}
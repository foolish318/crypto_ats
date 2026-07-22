package com.example.hft.exchange.binance;

import com.example.hft.marketdata.model.ActualMarketDataRecord;
import com.example.hft.marketdata.model.Quote;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;




public final class BinanceTickerParser {
    private static final int PRICE_SCALE = 100;
    private static final int SIZE_SCALE = 1_000;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public ActualMarketDataRecord parse(String payload, long sequenceNumber, long localReceivedEpochMillis,
                                        long rawReceivedNanos) throws Exception {
        JsonNode root = objectMapper.readTree(payload);
        JsonNode data = root.has("data") ? root.get("data") : root;
        String symbol = data.get("s").asText();
        long exchangeEventTimeMillis = data.get("E").asLong();
        long bidTicks = toTicks(data.get("b").asText());
        int bidSize = Math.max(1, toSize(data.get("B").asText()));
        long askTicks = toTicks(data.get("a").asText());
        int askSize = Math.max(1, toSize(data.get("A").asText()));
        Quote quote = Quote.of(sequenceNumber, symbol, bidTicks, bidSize, askTicks, askSize, rawReceivedNanos);
        long parsedNanos = System.nanoTime();
        return new ActualMarketDataRecord(quote, exchangeEventTimeMillis, localReceivedEpochMillis,
                rawReceivedNanos, parsedNanos, payload);
    }

    private static long toTicks(String value) {
        return new BigDecimal(value).multiply(BigDecimal.valueOf(PRICE_SCALE)).longValue();
    }

    private static int toSize(String value) {
        BigDecimal scaled = new BigDecimal(value).multiply(BigDecimal.valueOf(SIZE_SCALE));
        long size = scaled.longValue();
        return size > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) size;
    }
}

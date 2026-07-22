package com.example.hft.exchange.binance;

import com.example.hft.marketdata.model.Quote;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;




public final class BinanceBookTickerParser {
    private static final int PRICE_SCALE = 100;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public Quote parseQuote(String payload, long sequenceNumber, long receivedNanos) throws Exception {
        JsonNode root = objectMapper.readTree(payload);
        JsonNode data = root.has("data") ? root.get("data") : root;
        String symbol = data.get("s").asText();
        long bidTicks = toTicks(data.get("b").asText());
        int bidSize = toSize(data.get("B").asText());
        long askTicks = toTicks(data.get("a").asText());
        int askSize = toSize(data.get("A").asText());
        return Quote.of(sequenceNumber, symbol, bidTicks, Math.max(1, bidSize), askTicks, Math.max(1, askSize), receivedNanos);
    }

    private static long toTicks(String value) {
        return new BigDecimal(value).multiply(BigDecimal.valueOf(PRICE_SCALE)).longValue();
    }

    private static int toSize(String value) {
        BigDecimal scaled = new BigDecimal(value).multiply(BigDecimal.valueOf(1_000));
        long size = scaled.longValue();
        return size > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) size;
    }
}

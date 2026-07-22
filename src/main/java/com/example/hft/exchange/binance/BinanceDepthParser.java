package com.example.hft.exchange.binance;

import com.example.hft.marketdata.model.DepthUpdate;
import com.example.hft.marketdata.model.LocalOrderBook;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;



public final class BinanceDepthParser {
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DepthUpdate parseUpdate(String payload) throws Exception {
        JsonNode root = objectMapper.readTree(payload);
        JsonNode data = root.has("data") ? root.get("data") : root;
        return new DepthUpdate(
                data.get("s").asText(),
                data.get("E").asLong(),
                data.get("U").asLong(),
                data.get("u").asLong(),
                LocalOrderBook.parseLevels(data.get("b")),
                LocalOrderBook.parseLevels(data.get("a"))
        );
    }
}

package com.example.hft.exchange.coinbase;

import com.example.hft.exchange.AbstractWebSocketTopOfBookAdapter;
import com.example.hft.marketdata.model.TopOfBookSnapshot;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.net.URI;




public final class CoinbaseLevel2WebSocketAdapter extends AbstractWebSocketTopOfBookAdapter {
    private final String productId;

    public CoinbaseLevel2WebSocketAdapter(HttpClient httpClient, ObjectMapper objectMapper, String productId) {
        super(httpClient, objectMapper);
        this.productId = productId;
    }

    @Override
    public String exchange() {
        return "COINBASE";
    }

    @Override
    public String symbol() {
        return productId;
    }

    @Override
    protected URI uri() {
        return URI.create("wss://advanced-trade-ws.coinbase.com");
    }

    @Override
    protected void afterConnect(WebSocket webSocket) {
        String subscribe = "{\"type\":\"subscribe\",\"product_ids\":[\"" + productId + "\"],\"channel\":\"level2\"}";
        webSocket.sendText(subscribe, true).join();
    }

    @Override
    protected TopOfBookSnapshot tryParseSnapshot(JsonNode root, long startNanos) {
        if (!"l2_data".equals(root.path("channel").asText())) {
            return null;
        }
        JsonNode events = root.path("events");
        if (!events.isArray()) {
            return null;
        }
        BigDecimal bestBid = null;
        BigDecimal bestBidSize = null;
        BigDecimal bestAsk = null;
        BigDecimal bestAskSize = null;
        for (JsonNode event : events) {
            if (!productId.equals(event.path("product_id").asText())) {
                continue;
            }
            JsonNode updates = event.path("updates");
            for (JsonNode update : updates) {
                BigDecimal price = decimal(update.get("price_level"));
                BigDecimal size = decimal(update.get("new_quantity"));
                String side = update.path("side").asText();
                if (isBid(side)) {
                    if (bestBid == null || price.compareTo(bestBid) > 0) {
                        bestBid = price;
                        bestBidSize = size;
                    }
                } else if (isAsk(side)) {
                    if (bestAsk == null || price.compareTo(bestAsk) < 0) {
                        bestAsk = price;
                        bestAskSize = size;
                    }
                }
            }
        }
        if (bestBid == null || bestAsk == null) {
            return null;
        }
        return snapshot(bestBid, bestBidSize, bestAsk, bestAskSize, startNanos);
    }

    private static boolean isBid(String side) {
        return "bid".equalsIgnoreCase(side) || "buy".equalsIgnoreCase(side);
    }

    private static boolean isAsk(String side) {
        return "ask".equalsIgnoreCase(side) || "offer".equalsIgnoreCase(side) || "sell".equalsIgnoreCase(side);
    }
}

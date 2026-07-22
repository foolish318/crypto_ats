package com.example.hft.exchange.okx;

import com.example.hft.exchange.AbstractWebSocketTopOfBookAdapter;
import com.example.hft.marketdata.model.TopOfBookSnapshot;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.net.URI;




public final class OkxBooks5WebSocketAdapter extends AbstractWebSocketTopOfBookAdapter {
    private final String instrumentId;

    public OkxBooks5WebSocketAdapter(HttpClient httpClient, ObjectMapper objectMapper, String instrumentId) {
        super(httpClient, objectMapper);
        this.instrumentId = instrumentId;
    }

    @Override
    public String exchange() {
        return "OKX";
    }

    @Override
    public String symbol() {
        return instrumentId;
    }

    @Override
    protected URI uri() {
        return URI.create("wss://ws.okx.com:8443/ws/v5/public");
    }

    @Override
    protected void afterConnect(WebSocket webSocket) {
        String subscribe = "{\"op\":\"subscribe\",\"args\":[{\"channel\":\"books5\",\"instId\":\"" + instrumentId + "\"}]}";
        webSocket.sendText(subscribe, true).join();
    }

    @Override
    protected TopOfBookSnapshot tryParseSnapshot(JsonNode root, long startNanos) {
        if (!root.has("data")) {
            return null;
        }
        JsonNode data = root.get("data").get(0);
        if (data == null || !data.has("bids") || !data.has("asks")) {
            return null;
        }
        JsonNode bid = data.get("bids").get(0);
        JsonNode ask = data.get("asks").get(0);
        return snapshot(decimal(bid.get(0)), decimal(bid.get(1)), decimal(ask.get(0)), decimal(ask.get(1)), startNanos);
    }
}

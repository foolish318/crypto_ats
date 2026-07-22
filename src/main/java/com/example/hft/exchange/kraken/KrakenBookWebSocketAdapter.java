package com.example.hft.exchange.kraken;

import com.example.hft.exchange.AbstractWebSocketTopOfBookAdapter;
import com.example.hft.marketdata.model.TopOfBookSnapshot;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.net.URI;




public final class KrakenBookWebSocketAdapter extends AbstractWebSocketTopOfBookAdapter {
    private final String pair;

    public KrakenBookWebSocketAdapter(HttpClient httpClient, ObjectMapper objectMapper, String pair) {
        super(httpClient, objectMapper);
        this.pair = pair;
    }

    @Override
    public String exchange() {
        return "KRAKEN";
    }

    @Override
    public String symbol() {
        return pair;
    }

    @Override
    protected URI uri() {
        return URI.create("wss://ws.kraken.com/v2");
    }

    @Override
    protected void afterConnect(WebSocket webSocket) {
        String subscribe = "{\"method\":\"subscribe\",\"params\":{\"channel\":\"book\",\"symbol\":[\"" + pair + "\"],\"depth\":10,\"snapshot\":true}}";
        webSocket.sendText(subscribe, true).join();
    }

    @Override
    protected TopOfBookSnapshot tryParseSnapshot(JsonNode root, long startNanos) {
        if (!"book".equals(root.path("channel").asText()) || !"snapshot".equals(root.path("type").asText())) {
            return null;
        }
        JsonNode book = root.path("data").get(0);
        if (book == null || !pair.equals(book.path("symbol").asText())) {
            return null;
        }
        JsonNode bid = book.get("bids").get(0);
        JsonNode ask = book.get("asks").get(0);
        return snapshot(decimal(bid.get("price")), decimal(bid.get("qty")), decimal(ask.get("price")), decimal(ask.get("qty")), startNanos);
    }
}

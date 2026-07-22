package com.example.hft.exchange.binance;

import com.example.hft.exchange.AbstractWebSocketTopOfBookAdapter;
import com.example.hft.marketdata.model.TopOfBookSnapshot;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.net.URI;




public final class BinanceUsBookTickerWebSocketAdapter extends AbstractWebSocketTopOfBookAdapter {
    private final String symbol;

    public BinanceUsBookTickerWebSocketAdapter(HttpClient httpClient, ObjectMapper objectMapper, String symbol) {
        super(httpClient, objectMapper);
        this.symbol = symbol;
    }

    @Override
    public String exchange() {
        return "BINANCE_US";
    }

    @Override
    public String symbol() {
        return symbol;
    }

    @Override
    protected URI uri() {
        return URI.create("wss://stream.binance.us:9443/ws/" + symbol.toLowerCase() + "@depth5@100ms");
    }

    @Override
    protected TopOfBookSnapshot tryParseSnapshot(JsonNode root, long startNanos) {
        JsonNode data = root.has("data") ? root.get("data") : root;
        if (data.has("bids") && data.has("asks")) {
            JsonNode bid = data.get("bids").get(0);
            JsonNode ask = data.get("asks").get(0);
            return snapshot(decimal(bid.get(0)), decimal(bid.get(1)), decimal(ask.get(0)), decimal(ask.get(1)), startNanos);
        }
        if (data.has("b") && data.has("a")) {
            return snapshot(decimal(data.get("b")), decimal(data.get("B")), decimal(data.get("a")), decimal(data.get("A")), startNanos);
        }
        return null;
    }
}

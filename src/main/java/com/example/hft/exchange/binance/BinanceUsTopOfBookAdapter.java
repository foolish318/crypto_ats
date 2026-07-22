package com.example.hft.exchange.binance;

import com.example.hft.exchange.AbstractHttpTopOfBookAdapter;
import com.example.hft.marketdata.model.TopOfBookSnapshot;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.net.URI;




public final class BinanceUsTopOfBookAdapter extends AbstractHttpTopOfBookAdapter {
    private final String symbol;

    public BinanceUsTopOfBookAdapter(HttpClient httpClient, ObjectMapper objectMapper, String symbol) {
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
    public TopOfBookSnapshot fetch() throws Exception {
        long start = System.nanoTime();
        URI uri = URI.create("https://api.binance.us/api/v3/ticker/bookTicker?symbol=" + symbol);
        JsonNode root = getJson(uri);
        return snapshot(exchange(), symbol,
                decimal(root.get("bidPrice")),
                decimal(root.get("bidQty")),
                decimal(root.get("askPrice")),
                decimal(root.get("askQty")),
                start);
    }
}

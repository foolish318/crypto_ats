package com.example.hft.exchange.coinbase;

import com.example.hft.exchange.AbstractHttpTopOfBookAdapter;
import com.example.hft.marketdata.model.TopOfBookSnapshot;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.net.URI;




public final class CoinbaseTopOfBookAdapter extends AbstractHttpTopOfBookAdapter {
    private final String productId;

    public CoinbaseTopOfBookAdapter(HttpClient httpClient, ObjectMapper objectMapper, String productId) {
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
    public TopOfBookSnapshot fetch() throws Exception {
        long start = System.nanoTime();
        URI uri = URI.create("https://api.exchange.coinbase.com/products/" + productId + "/book?level=1");
        JsonNode root = getJson(uri);
        JsonNode bid = root.get("bids").get(0);
        JsonNode ask = root.get("asks").get(0);
        return snapshot(exchange(), productId,
                decimal(bid.get(0)),
                decimal(bid.get(1)),
                decimal(ask.get(0)),
                decimal(ask.get(1)),
                start);
    }
}

package com.example.hft.exchange.okx;

import com.example.hft.exchange.AbstractHttpTopOfBookAdapter;
import com.example.hft.marketdata.model.TopOfBookSnapshot;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.net.URI;




public final class OkxTopOfBookAdapter extends AbstractHttpTopOfBookAdapter {
    private final String instrumentId;

    public OkxTopOfBookAdapter(HttpClient httpClient, ObjectMapper objectMapper, String instrumentId) {
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
    public TopOfBookSnapshot fetch() throws Exception {
        long start = System.nanoTime();
        URI uri = URI.create("https://www.okx.com/api/v5/market/books?instId=" + instrumentId + "&sz=1");
        JsonNode root = getJson(uri);
        if (!"0".equals(root.path("code").asText())) {
            throw new IllegalStateException("OKX error: " + root.path("msg").asText());
        }
        JsonNode book = root.get("data").get(0);
        JsonNode bid = book.get("bids").get(0);
        JsonNode ask = book.get("asks").get(0);
        return snapshot(exchange(), instrumentId,
                decimal(bid.get(0)),
                decimal(bid.get(1)),
                decimal(ask.get(0)),
                decimal(ask.get(1)),
                start);
    }
}

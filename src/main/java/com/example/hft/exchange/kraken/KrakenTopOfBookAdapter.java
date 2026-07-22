package com.example.hft.exchange.kraken;

import com.example.hft.exchange.AbstractHttpTopOfBookAdapter;
import com.example.hft.marketdata.model.TopOfBookSnapshot;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;




public final class KrakenTopOfBookAdapter extends AbstractHttpTopOfBookAdapter {
    private final String displaySymbol;
    private final String requestPair;

    public KrakenTopOfBookAdapter(HttpClient httpClient, ObjectMapper objectMapper, String displaySymbol, String requestPair) {
        super(httpClient, objectMapper);
        this.displaySymbol = displaySymbol;
        this.requestPair = requestPair;
    }

    @Override
    public String exchange() {
        return "KRAKEN";
    }

    @Override
    public String symbol() {
        return displaySymbol;
    }

    @Override
    public TopOfBookSnapshot fetch() throws Exception {
        long start = System.nanoTime();
        String encodedPair = URLEncoder.encode(requestPair, StandardCharsets.UTF_8);
        URI uri = URI.create("https://api.kraken.com/0/public/Depth?pair=" + encodedPair + "&count=1");
        JsonNode root = getJson(uri);
        JsonNode errors = root.get("error");
        if (errors != null && errors.size() > 0) {
            throw new IllegalStateException("Kraken error: " + errors);
        }
        JsonNode result = root.get("result");
        Iterator<String> names = result.fieldNames();
        if (!names.hasNext()) {
            throw new IllegalStateException("Kraken response has no order book result");
        }
        JsonNode book = result.get(names.next());
        JsonNode bid = book.get("bids").get(0);
        JsonNode ask = book.get("asks").get(0);
        return snapshot(exchange(), displaySymbol,
                decimal(bid.get(0)),
                decimal(bid.get(1)),
                decimal(ask.get(0)),
                decimal(ask.get(1)),
                start);
    }
}

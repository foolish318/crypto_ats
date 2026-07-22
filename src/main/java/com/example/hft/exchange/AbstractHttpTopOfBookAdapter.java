package com.example.hft.exchange;

import com.example.hft.marketdata.model.TopOfBookSnapshot;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;




public abstract class AbstractHttpTopOfBookAdapter implements CustomTopOfBookAdapter {
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    protected AbstractHttpTopOfBookAdapter(HttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    protected final JsonNode getJson(URI uri) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .header("User-Agent", "hft-java-learning/0.1")
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("HTTP " + response.statusCode() + " from " + uri + ": " + response.body());
        }
        return objectMapper.readTree(response.body());
    }

    protected final TopOfBookSnapshot snapshot(String exchange, String symbol, BigDecimal bidPrice, BigDecimal bidSize,
                                               BigDecimal askPrice, BigDecimal askSize, long startNanos) {
        return new TopOfBookSnapshot("CUSTOM", exchange, symbol, bidPrice, bidSize, askPrice, askSize,
                Instant.now(), System.nanoTime() - startNanos);
    }

    protected static BigDecimal decimal(JsonNode node) {
        return new BigDecimal(node.asText());
    }
}

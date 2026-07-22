package com.example.hft.exchange;

import com.example.hft.marketdata.model.TopOfBookSnapshot;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;




public abstract class AbstractWebSocketTopOfBookAdapter implements CustomWebSocketTopOfBookAdapter {
    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    protected AbstractWebSocketTopOfBookAdapter(HttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public final TopOfBookSnapshot fetch() throws Exception {
        long start = System.nanoTime();
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<TopOfBookSnapshot> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();

        WebSocket webSocket = httpClient.newWebSocketBuilder()
                .header("User-Agent", "hft-java-learning/0.1")
                .buildAsync(uri(), new WebSocket.Listener() {
                    private final StringBuilder buffer = new StringBuilder();

                    @Override
                    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                        buffer.append(data);
                        if (!last) {
                            webSocket.request(1);
                            return null;
                        }
                        try {
                            JsonNode root = objectMapper.readTree(buffer.toString());
                            TopOfBookSnapshot snapshot = tryParseSnapshot(root, start);
                            if (snapshot != null) {
                                result.set(snapshot);
                                done.countDown();
                                webSocket.abort();
                            }
                        } catch (Throwable t) {
                            failure.compareAndSet(null, t);
                            done.countDown();
                            webSocket.abort();
                        } finally {
                            buffer.setLength(0);
                            if (result.get() == null && failure.get() == null) {
                                webSocket.request(1);
                            }
                        }
                        return null;
                    }

                    @Override
                    public void onError(WebSocket webSocket, Throwable error) {
                        failure.compareAndSet(null, error);
                        done.countDown();
                    }
                })
                .join();

        webSocket.request(1);
        afterConnect(webSocket);
        boolean completed = done.await(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        if (!completed) {
            webSocket.abort();
            throw new IllegalStateException("timed out waiting for " + exchange() + " " + symbol() + " WebSocket data");
        }
        if (failure.get() != null) {
            throw new IllegalStateException("failed to read " + exchange() + " " + symbol() + " WebSocket data", failure.get());
        }
        return result.get();
    }

    protected abstract java.net.URI uri();

    protected void afterConnect(WebSocket webSocket) {
    }

    protected abstract TopOfBookSnapshot tryParseSnapshot(JsonNode root, long startNanos);

    protected final TopOfBookSnapshot snapshot(BigDecimal bidPrice, BigDecimal bidSize, BigDecimal askPrice,
                                               BigDecimal askSize, long startNanos) {
        return new TopOfBookSnapshot("CUSTOM_WS", exchange(), symbol(), bidPrice, bidSize, askPrice, askSize,
                Instant.now(), System.nanoTime() - startNanos);
    }

    protected static BigDecimal decimal(JsonNode node) {
        return new BigDecimal(node.asText());
    }
}

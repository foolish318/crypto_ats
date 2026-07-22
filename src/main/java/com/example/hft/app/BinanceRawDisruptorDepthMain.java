package com.example.hft.app;

import com.example.hft.exchange.binance.BinanceBookTickerSource;
import com.example.hft.marketdata.model.LocalOrderBook;
import com.example.hft.marketdata.model.RawDepthPayload;
import com.example.hft.pipeline.RawDisruptorDepthPipeline;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedWriter;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;




public final class BinanceRawDisruptorDepthMain {
    private static final List<String> DEFAULT_SYMBOLS = List.of("BTCUSDT", "ETHUSDT", "BNBUSDT", "SOLUSDT", "XRPUSDT");
    private static final int DEFAULT_LIMIT = 500;
    private static final int DEFAULT_PARTITIONS = 4;
    private static final int BOOK_SNAPSHOT_LIMIT = 100;
    private static final Duration TIMEOUT = Duration.ofSeconds(90);
    private static final String SNAPSHOT_URI = "https://api.binance.us/api/v3/depth?symbol=%s&limit=%d";

    private BinanceRawDisruptorDepthMain() {
    }

    public static void main(String[] args) throws Exception {
        int limit = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_LIMIT;
        List<String> symbols = args.length > 1 ? List.of(args[1].split(",")) : DEFAULT_SYMBOLS;
        int partitions = args.length > 2 ? Integer.parseInt(args[2]) : DEFAULT_PARTITIONS;
        Path output = args.length > 3 ? Path.of(args[3]) : defaultOutputPath();

        HttpClient httpClient = HttpClient.newHttpClient();
        Map<String, LocalOrderBook> books = loadSnapshots(httpClient, symbols);
        RawDisruptorDepthPipeline pipeline = new RawDisruptorDepthPipeline(books, partitions, limit);
        List<RawDepthPayload> rawRecords = new ArrayList<>(limit);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        pipeline.start();
        try {
            collectFromWebSocket(httpClient, limit, symbols, pipeline, rawRecords, failure);
        } finally {
            pipeline.stopAndJoin();
        }

        if (failure.get() != null) {
            throw new IllegalStateException("raw depth WebSocket collection failed", failure.get());
        }

        writeJsonLines(output, rawRecords, partitions);
        System.out.println("stored raw Binance.US depth messages=" + rawRecords.size() + " file=" + output);
        System.out.println(pipeline.stats().toDisplayLine());
    }

    private static Map<String, LocalOrderBook> loadSnapshots(HttpClient httpClient, List<String> symbols) throws Exception {
        Map<String, LocalOrderBook> books = new HashMap<>();
        for (String symbol : symbols) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(String.format(SNAPSHOT_URI, symbol, BOOK_SNAPSHOT_LIMIT)))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            String body = httpClient.send(request, HttpResponse.BodyHandlers.ofString()).body();
            LocalOrderBook book = new LocalOrderBook();
            book.loadSnapshot(body);
            books.put(symbol.toUpperCase(Locale.ROOT), book);
        }
        return books;
    }

    private static void collectFromWebSocket(HttpClient httpClient, int limit, List<String> symbols,
                                             RawDisruptorDepthPipeline pipeline, List<RawDepthPayload> rawRecords,
                                             AtomicReference<Throwable> failure) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        URI uri = URI.create(BinanceBookTickerSource.BINANCE_US_STREAM_URI + streamNames(symbols));

        WebSocket webSocket = httpClient.newWebSocketBuilder()
                .buildAsync(uri, new WebSocket.Listener() {
                    private final StringBuilder buffer = new StringBuilder();
                    private long sequenceNumber;
                    private int received;
                    private boolean doneCollecting;

                    @Override
                    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                        if (doneCollecting) {
                            return null;
                        }
                        long localReceivedEpochMillis = System.currentTimeMillis();
                        long rawReceivedNanos = System.nanoTime();
                        buffer.append(data);
                        if (!last) {
                            webSocket.request(1);
                            return null;
                        }

                        try {
                            String payload = buffer.toString();
                            String symbol = extractSymbol(payload);
                            RawDepthPayload raw = new RawDepthPayload(++sequenceNumber, symbol, localReceivedEpochMillis,
                                    rawReceivedNanos, payload);

                            long publishedNanos = System.nanoTime();
                            pipeline.publish(raw, publishedNanos);
                            pipeline.stats().addProducerOfferNanos(System.nanoTime() - publishedNanos);
                            rawRecords.add(raw);
                            received++;

                            if (received >= limit) {
                                doneCollecting = true;
                                done.countDown();
                                webSocket.abort();
                            }
                        } catch (Throwable t) {
                            doneCollecting = true;
                            failure.compareAndSet(null, t);
                            done.countDown();
                            webSocket.abort();
                        } finally {
                            buffer.setLength(0);
                            if (!doneCollecting) {
                                webSocket.request(1);
                            }
                        }
                        return null;
                    }

                    @Override
                    public void onError(WebSocket webSocket, Throwable error) {
                        if (!doneCollecting) {
                            failure.compareAndSet(null, error);
                            done.countDown();
                        }
                    }
                })
                .join();

        webSocket.request(1);
        boolean completed = done.await(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        if (!completed) {
            webSocket.abort();
            throw new IllegalStateException("timed out waiting for live Binance.US depth data from " + uri);
        }
    }

    private static String extractSymbol(String payload) {
        String marker = "\"s\":\"";
        int start = payload.indexOf(marker);
        if (start < 0) {
            return "";
        }
        int valueStart = start + marker.length();
        int valueEnd = payload.indexOf('"', valueStart);
        if (valueEnd < 0) {
            return "";
        }
        return payload.substring(valueStart, valueEnd);
    }

    private static String streamNames(List<String> symbols) {
        List<String> streams = new ArrayList<>(symbols.size());
        for (String symbol : symbols) {
            streams.add(symbol.toLowerCase(Locale.ROOT) + "@depth@100ms");
        }
        return String.join("/", streams);
    }

    private static Path defaultOutputPath() {
        return Path.of("data", "binance-raw-disruptor-" + Instant.now().toString().replace(":", "") + ".jsonl");
    }

    private static void writeJsonLines(Path output, List<RawDepthPayload> rawRecords, int partitions) throws Exception {
        Files.createDirectories(output.getParent());
        ObjectMapper mapper = new ObjectMapper();
        try (BufferedWriter writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
            for (RawDepthPayload raw : rawRecords) {
                ObjectNode line = mapper.createObjectNode();
                line.put("mode", "v11-raw-disruptor");
                line.put("partitions", partitions);
                line.put("sequenceNumber", raw.sequenceNumber());
                line.put("routingSymbol", raw.routingSymbol());
                line.put("localReceivedEpochMillis", raw.localReceivedEpochMillis());
                line.put("rawPayload", raw.rawPayload());
                writer.write(mapper.writeValueAsString(line));
                writer.newLine();
            }
        }
    }
}

package com.example.hft.app;

import com.example.hft.datasource.DataSourceModuleVersion;
import com.example.hft.datasource.deepbook.DeepBookSourceCatalog;
import com.example.hft.datasource.deepbook.DeepBookSourceDefinition;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.BufferedWriter;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;


public final class DeepBookSourceDiscoveryMain {
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration WEB_SOCKET_TIMEOUT = Duration.ofSeconds(20);

    private DeepBookSourceDiscoveryMain() {
    }

    public static void main(String[] args) throws Exception {
        Path outputDir = args.length > 0 ? Path.of(args[0]) : Path.of("data");
        Files.createDirectories(outputDir);
        Path output = outputDir.resolve("deep-book-sources-v19-" + runId() + ".jsonl");
        ObjectMapper mapper = new ObjectMapper();
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(HTTP_TIMEOUT).build();
        List<DeepBookSourceDefinition> sources = DeepBookSourceCatalog.defaultSources();
        List<DeepBookProbeResult> results = new ArrayList<>();

        try (BufferedWriter writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
            for (DeepBookSourceDefinition source : sources) {
                DeepBookProbeResult result = probe(httpClient, mapper, source);
                results.add(result);
                writer.write(toJson(mapper, result));
                writer.newLine();
                System.out.println(result.toDisplayLine());
            }
        }

        long successes = results.stream().filter(DeepBookProbeResult::success).count();
        System.out.println("DEEP_BOOK_SOURCE_SUMMARY version=" + DataSourceModuleVersion.VERSION
                + " sources=" + results.size()
                + " successes=" + successes
                + " failures=" + (results.size() - successes)
                + " output=" + output);
        if (successes == 0) {
            throw new IllegalStateException("no deep-book sources returned usable data");
        }
    }

    private static DeepBookProbeResult probe(HttpClient httpClient, ObjectMapper mapper,
                                             DeepBookSourceDefinition source) {
        long startNanos = System.nanoTime();
        try {
            if ("BINANCE_US".equals(source.exchange())) {
                return probeBinance(httpClient, mapper, source, startNanos);
            }
            String payload = receiveFirstBookPayload(httpClient, mapper, source);
            return parseWebSocketBook(mapper, source, startNanos, payload);
        } catch (Exception e) {
            return DeepBookProbeResult.failure(source, elapsedMillis(startNanos), e.getClass().getSimpleName()
                    + ": " + e.getMessage());
        }
    }

    private static DeepBookProbeResult probeBinance(HttpClient httpClient, ObjectMapper mapper,
                                                    DeepBookSourceDefinition source, long startNanos) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(source.snapshotUri())
                .timeout(HTTP_TIMEOUT)
                .GET()
                .build();
        String snapshotPayload = httpClient.send(request, HttpResponse.BodyHandlers.ofString()).body();
        JsonNode snapshot = mapper.readTree(snapshotPayload);
        int snapshotBids = snapshot.path("bids").size();
        int snapshotAsks = snapshot.path("asks").size();
        String lastUpdateId = snapshot.path("lastUpdateId").asText("");

        String updatePayload = receiveFirstBookPayload(httpClient, mapper, source);
        JsonNode update = mapper.readTree(updatePayload);
        int updateBids = update.path("b").size();
        int updateAsks = update.path("a").size();
        String sequence = "lastUpdateId=" + lastUpdateId
                + ",U=" + update.path("U").asText("")
                + ",u=" + update.path("u").asText("");
        return DeepBookProbeResult.success(source, elapsedMillis(startNanos), snapshotBids, snapshotAsks,
                updateBids, updateAsks, sequence, "", snapshotPayload.length() + updatePayload.length(),
                "REST snapshot + WS diff update");
    }
    private static String receiveFirstBookPayload(HttpClient httpClient, ObjectMapper mapper,
                                                  DeepBookSourceDefinition source) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<String> payload = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();

        WebSocket webSocket = httpClient.newWebSocketBuilder()
                .header("User-Agent", "hft-java-learning/0.1")
                .buildAsync(source.webSocketUri(), new WebSocket.Listener() {
                    private final StringBuilder buffer = new StringBuilder();

                    @Override
                    public void onOpen(WebSocket webSocket) {
                        if (source.hasSubscribeMessage()) {
                            webSocket.sendText(source.subscribeMessage(), true);
                        }
                        webSocket.request(1);
                    }

                    @Override
                    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                        buffer.append(data);
                        if (!last) {
                            webSocket.request(1);
                            return null;
                        }

                        String raw = buffer.toString();
                        buffer.setLength(0);
                        try {
                            JsonNode root = mapper.readTree(raw);
                            if (isBookPayload(source, root)) {
                                payload.compareAndSet(null, raw);
                                done.countDown();
                                webSocket.abort();
                                return null;
                            }
                        } catch (Exception ignored) {
                            // Ignore subscription acknowledgements or non-book control messages.
                        }
                        webSocket.request(1);
                        return null;
                    }

                    @Override
                    public void onError(WebSocket webSocket, Throwable error) {
                        failure.compareAndSet(null, error);
                        done.countDown();
                    }
                })
                .join();

        boolean completed = done.await(WEB_SOCKET_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        if (!completed) {
            webSocket.abort();
            throw new IllegalStateException("timed out waiting for deep-book payload from " + source.id());
        }
        if (failure.get() != null) {
            throw new IllegalStateException("deep-book websocket failed for " + source.id(), failure.get());
        }
        if (payload.get() == null) {
            throw new IllegalStateException("no deep-book payload for " + source.id());
        }
        return payload.get();
    }

    private static boolean isBookPayload(DeepBookSourceDefinition source, JsonNode root) {
        return switch (source.exchange()) {
            case "BINANCE_US" -> root.has("b") && root.has("a") && root.has("U") && root.has("u");
            case "OKX" -> root.has("data") && root.path("arg").path("channel").asText().equals(source.channel());
            case "KRAKEN" -> "book".equals(root.path("channel").asText())
                    && "snapshot".equals(root.path("type").asText())
                    && root.has("data");
            default -> false;
        };
    }

    private static DeepBookProbeResult parseWebSocketBook(ObjectMapper mapper, DeepBookSourceDefinition source,
                                                          long startNanos, String payload) throws Exception {
        JsonNode root = mapper.readTree(payload);
        if ("OKX".equals(source.exchange())) {
            JsonNode book = root.path("data").get(0);
            String sequence = "seqId=" + book.path("seqId").asText("")
                    + ",prevSeqId=" + book.path("prevSeqId").asText("");
            return DeepBookProbeResult.success(source, elapsedMillis(startNanos), book.path("bids").size(),
                    book.path("asks").size(), 0, 0, sequence, book.path("checksum").asText(""),
                    payload.length(), root.path("action").asText("snapshot/update"));
        }
        if ("KRAKEN".equals(source.exchange())) {
            JsonNode book = root.path("data").get(0);
            String sequence = "timestamp=" + book.path("timestamp").asText("");
            return DeepBookProbeResult.success(source, elapsedMillis(startNanos), book.path("bids").size(),
                    book.path("asks").size(), 0, 0, sequence, book.path("checksum").asText(""),
                    payload.length(), root.path("type").asText("snapshot"));
        }
        throw new IllegalArgumentException("unsupported deep-book source " + source.exchange());
    }

    private static String toJson(ObjectMapper mapper, DeepBookProbeResult result) throws Exception {
        ObjectNode line = mapper.createObjectNode();
        line.put("version", DataSourceModuleVersion.VERSION);
        line.put("sourceId", result.sourceId());
        line.put("exchange", result.exchange());
        line.put("symbol", result.symbol());
        line.put("channel", result.channel());
        line.put("configuredDepthLevels", result.configuredDepthLevels());
        line.put("success", result.success());
        line.put("snapshotBidLevels", result.snapshotBidLevels());
        line.put("snapshotAskLevels", result.snapshotAskLevels());
        line.put("updateBidLevels", result.updateBidLevels());
        line.put("updateAskLevels", result.updateAskLevels());
        line.put("sequence", result.sequence());
        line.put("checksum", result.checksum());
        line.put("loadMillis", result.loadMillis());
        line.put("rawBytes", result.rawBytes());
        line.put("requiresAuthentication", result.requiresAuthentication());
        line.put("message", result.message());
        return mapper.writeValueAsString(line);
    }
    private static long elapsedMillis(long startNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
    }

    private static String runId() {
        return Instant.now().toString().replace(":", "").replace(".", "");
    }

    private record DeepBookProbeResult(
            String sourceId,
            String exchange,
            String symbol,
            String channel,
            int configuredDepthLevels,
            boolean requiresAuthentication,
            boolean success,
            int snapshotBidLevels,
            int snapshotAskLevels,
            int updateBidLevels,
            int updateAskLevels,
            String sequence,
            String checksum,
            long loadMillis,
            long rawBytes,
            String message
    ) {
        static DeepBookProbeResult success(DeepBookSourceDefinition source, long loadMillis,
                                           int snapshotBidLevels, int snapshotAskLevels,
                                           int updateBidLevels, int updateAskLevels,
                                           String sequence, String checksum, long rawBytes,
                                           String message) {
            return new DeepBookProbeResult(source.id(), source.exchange(), source.symbol(), source.channel(),
                    source.depthLevels(), source.requiresAuthentication(), true, snapshotBidLevels, snapshotAskLevels,
                    updateBidLevels, updateAskLevels, sequence, checksum, loadMillis, rawBytes, message);
        }

        static DeepBookProbeResult failure(DeepBookSourceDefinition source, long loadMillis, String message) {
            return new DeepBookProbeResult(source.id(), source.exchange(), source.symbol(), source.channel(),
                    source.depthLevels(), source.requiresAuthentication(), false, 0, 0, 0, 0, "", "", loadMillis,
                    0, message);
        }

        String toDisplayLine() {
            return "DEEP_BOOK_SOURCE"
                    + " version=" + DataSourceModuleVersion.VERSION
                    + " source=" + sourceId
                    + " exchange=" + exchange
                    + " symbol=" + symbol
                    + " channel=" + channel
                    + " configuredDepth=" + configuredDepthLevels
                    + " auth=" + requiresAuthentication
                    + " success=" + success
                    + " snapshotBidLevels=" + snapshotBidLevels
                    + " snapshotAskLevels=" + snapshotAskLevels
                    + " updateBidLevels=" + updateBidLevels
                    + " updateAskLevels=" + updateAskLevels
                    + " sequence=" + sequence.replace(' ', '_')
                    + " checksum=" + checksum
                    + " loadMs=" + String.format(Locale.ROOT, "%d", loadMillis)
                    + " rawBytes=" + rawBytes
                    + " message=" + message.replace(' ', '_');
        }
    }
}
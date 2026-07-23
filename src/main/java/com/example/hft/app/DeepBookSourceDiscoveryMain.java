package com.example.hft.app;

import com.example.hft.datasource.DataSourceModuleVersion;
import com.example.hft.datasource.deepbook.DeepBookSourceCatalog;
import com.example.hft.datasource.deepbook.DeepBookSourceDefinition;
import com.example.hft.datasource.deepbook.quality.DeepBookQualityReport;
import com.example.hft.datasource.deepbook.quality.DeepBookQualityValidator;
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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;


public final class DeepBookSourceDiscoveryMain {
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration WEB_SOCKET_TIMEOUT = Duration.ofSeconds(20);
    private static final int QUALITY_SAMPLE_MESSAGES = 2;

    private DeepBookSourceDiscoveryMain() {
    }

    public static void main(String[] args) throws Exception {
        Path outputDir = args.length > 0 ? Path.of(args[0]) : Path.of("data");
        Files.createDirectories(outputDir);
        Path output = outputDir.resolve("deep-book-quality-v20-" + runId() + ".jsonl");
        ObjectMapper mapper = new ObjectMapper();
        DeepBookQualityValidator qualityValidator = new DeepBookQualityValidator();
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(HTTP_TIMEOUT).build();
        List<DeepBookSourceDefinition> sources = DeepBookSourceCatalog.defaultSources();
        List<DeepBookProbeResult> results = new ArrayList<>();

        try (BufferedWriter writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
            for (DeepBookSourceDefinition source : sources) {
                DeepBookProbeResult result = probe(httpClient, mapper, qualityValidator, source);
                results.add(result);
                writer.write(toJson(mapper, result));
                writer.newLine();
                System.out.println(result.toDisplayLine());
            }
        }

        long connected = results.stream().filter(DeepBookProbeResult::transportSuccess).count();
        long usable = results.stream().filter(DeepBookProbeResult::qualityAccepted).count();
        System.out.println("DEEP_BOOK_SOURCE_SUMMARY version=" + DataSourceModuleVersion.VERSION
                + " sources=" + results.size()
                + " connected=" + connected
                + " qualityAccepted=" + usable
                + " rejected=" + (results.size() - usable)
                + " output=" + output);
        if (usable == 0) {
            throw new IllegalStateException("no deep-book sources passed the quality gate");
        }
    }

    private static DeepBookProbeResult probe(
            HttpClient httpClient,
            ObjectMapper mapper,
            DeepBookQualityValidator qualityValidator,
            DeepBookSourceDefinition source
    ) {
        long startNanos = System.nanoTime();
        try {
            if ("BINANCE_US".equals(source.exchange())) {
                return probeBinance(httpClient, mapper, qualityValidator, source, startNanos);
            }
            List<String> payloads = receiveBookPayloads(httpClient, mapper, source, QUALITY_SAMPLE_MESSAGES);
            return probeWebSocketBook(mapper, qualityValidator, source, startNanos, payloads);
        } catch (Exception e) {
            return DeepBookProbeResult.failure(source, elapsedMillis(startNanos), e.getClass().getSimpleName()
                    + ": " + e.getMessage());
        }
    }

    private static DeepBookProbeResult probeBinance(
            HttpClient httpClient,
            ObjectMapper mapper,
            DeepBookQualityValidator qualityValidator,
            DeepBookSourceDefinition source,
            long startNanos
    ) throws Exception {
        BookMessageCapture capture = openBookCapture(httpClient, mapper, source);
        try {
            HttpRequest request = HttpRequest.newBuilder(source.snapshotUri())
                    .timeout(HTTP_TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException("snapshot HTTP status " + response.statusCode());
            }
            String snapshotPayload = response.body();
            JsonNode snapshot = mapper.readTree(snapshotPayload);
            int snapshotBids = snapshot.path("bids").size();
            int snapshotAsks = snapshot.path("asks").size();
            long lastUpdateId = snapshot.path("lastUpdateId").asLong(-1);
            if (lastUpdateId <= 0) {
                throw new IllegalStateException("snapshot missing lastUpdateId");
            }

            List<String> updatePayloads = awaitBinanceBridge(
                    capture, mapper, lastUpdateId, QUALITY_SAMPLE_MESSAGES, WEB_SOCKET_TIMEOUT);
            JsonNode update = mapper.readTree(updatePayloads.get(updatePayloads.size() - 1));
            int updateBids = update.path("b").size();
            int updateAsks = update.path("a").size();
            String sequence = "lastUpdateId=" + lastUpdateId
                    + ",lastU=" + update.path("U").asText("")
                    + ",lastu=" + update.path("u").asText("");
            DeepBookQualityReport quality = qualityValidator.validate(
                    source, snapshotPayload, updatePayloads, Instant.now());
            long rawBytes = snapshotPayload.length()
                    + capture.payloads().stream().mapToLong(String::length).sum();
            return DeepBookProbeResult.connected(
                    source,
                    elapsedMillis(startNanos),
                    snapshotBids,
                    snapshotAsks,
                    updateBids,
                    updateAsks,
                    sequence,
                    "",
                    rawBytes,
                    "WS buffer + REST snapshot + bridged diff updates",
                    quality
            );
        } finally {
            capture.close();
        }
    }

    private static List<String> awaitBinanceBridge(
            BookMessageCapture capture,
            ObjectMapper mapper,
            long snapshotUpdateId,
            int requiredMessages,
            Duration timeout
    ) throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        int observedMessages = -1;
        while (System.nanoTime() < deadline) {
            List<String> buffered = capture.payloads();
            List<String> selected = selectBinanceBridge(
                    mapper, snapshotUpdateId, buffered, requiredMessages);
            if (selected.size() == requiredMessages) {
                return selected;
            }
            if (buffered.size() == observedMessages) {
                capture.awaitChange(observedMessages, deadline - System.nanoTime());
            }
            observedMessages = buffered.size();
        }
        throw new IllegalStateException("timed out waiting for Binance snapshot bridge");
    }

    private static List<String> selectBinanceBridge(
            ObjectMapper mapper,
            long snapshotUpdateId,
            List<String> buffered,
            int requiredMessages
    ) throws Exception {
        List<String> selected = new ArrayList<>(requiredMessages);
        long expected = snapshotUpdateId + 1;
        for (String payload : buffered) {
            JsonNode update = mapper.readTree(payload);
            long firstUpdateId = update.path("U").asLong(-1);
            long finalUpdateId = update.path("u").asLong(-1);
            if (firstUpdateId <= 0 || finalUpdateId <= 0 || firstUpdateId > finalUpdateId) {
                throw new IllegalStateException("invalid Binance U/u values");
            }
            if (finalUpdateId < expected) {
                continue;
            }
            boolean contiguous = selected.isEmpty()
                    ? firstUpdateId <= expected && finalUpdateId >= expected
                    : firstUpdateId == expected;
            if (!contiguous) {
                throw new IllegalStateException("Binance bridge gap: expected=" + expected
                        + " U=" + firstUpdateId + " u=" + finalUpdateId);
            }
            selected.add(payload);
            expected = finalUpdateId + 1;
            if (selected.size() == requiredMessages) {
                return selected;
            }
        }
        return selected;
    }

    private static List<String> receiveBookPayloads(
            HttpClient httpClient,
            ObjectMapper mapper,
            DeepBookSourceDefinition source,
            int messageCount
    ) throws Exception {
        BookMessageCapture capture = openBookCapture(httpClient, mapper, source);
        try {
            return capture.awaitMessages(messageCount, WEB_SOCKET_TIMEOUT);
        } finally {
            capture.close();
        }
    }

    private static BookMessageCapture openBookCapture(
            HttpClient httpClient,
            ObjectMapper mapper,
            DeepBookSourceDefinition source
    ) throws Exception {
        BookMessageCapture capture = new BookMessageCapture(mapper, source);
        httpClient.newWebSocketBuilder()
                .header("User-Agent", "hft-java-learning/0.1")
                .buildAsync(source.webSocketUri(), capture)
                .join();
        capture.awaitOpen(WEB_SOCKET_TIMEOUT);
        return capture;
    }

    private static boolean isBookPayload(DeepBookSourceDefinition source, JsonNode root) {
        return switch (source.exchange()) {
            case "BINANCE_US" -> root.has("b") && root.has("a") && root.has("U") && root.has("u");
            case "OKX" -> root.has("data") && root.path("arg").path("channel").asText().equals(source.channel());
            case "KRAKEN" -> "book".equals(root.path("channel").asText())
                    && ("snapshot".equals(root.path("type").asText())
                    || "update".equals(root.path("type").asText()))
                    && root.has("data");
            default -> false;
        };
    }

    private static DeepBookProbeResult probeWebSocketBook(
            ObjectMapper mapper,
            DeepBookQualityValidator qualityValidator,
            DeepBookSourceDefinition source,
            long startNanos,
            List<String> payloads
    ) throws Exception {
        String firstPayload = payloads.get(0);
        String lastPayload = payloads.get(payloads.size() - 1);
        JsonNode root = mapper.readTree(firstPayload);
        JsonNode lastRoot = mapper.readTree(lastPayload);
        DeepBookQualityReport quality = qualityValidator.validate(source, null, payloads, Instant.now());
        long rawBytes = payloads.stream().mapToLong(String::length).sum();

        if ("OKX".equals(source.exchange())) {
            JsonNode book = root.path("data").get(0);
            JsonNode lastBook = lastRoot.path("data").get(0);
            String sequence = "firstSeqId=" + book.path("seqId").asText("")
                    + ",lastSeqId=" + lastBook.path("seqId").asText("")
                    + ",lastPrevSeqId=" + lastBook.path("prevSeqId").asText("");
            return DeepBookProbeResult.connected(
                    source,
                    elapsedMillis(startNanos),
                    book.path("bids").size(),
                    book.path("asks").size(),
                    lastBook.path("bids").size(),
                    lastBook.path("asks").size(),
                    sequence,
                    book.path("checksum").asText(""),
                    rawBytes,
                    "WS snapshot + incremental update",
                    quality
            );
        }
        if ("KRAKEN".equals(source.exchange())) {
            JsonNode book = root.path("data").get(0);
            JsonNode lastBook = lastRoot.path("data").get(0);
            String sequence = "firstTimestamp=" + book.path("timestamp").asText("")
                    + ",lastTimestamp=" + lastBook.path("timestamp").asText("");
            return DeepBookProbeResult.connected(
                    source,
                    elapsedMillis(startNanos),
                    book.path("bids").size(),
                    book.path("asks").size(),
                    lastBook.path("bids").size(),
                    lastBook.path("asks").size(),
                    sequence,
                    lastBook.path("checksum").asText(""),
                    rawBytes,
                    "WS snapshot + CRC32-checked update",
                    quality
            );
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
        line.put("success", result.qualityAccepted());
        line.put("transportSuccess", result.transportSuccess());
        line.put("qualityAccepted", result.qualityAccepted());
        line.put("snapshotBidLevels", result.snapshotBidLevels());
        line.put("snapshotAskLevels", result.snapshotAskLevels());
        line.put("updateBidLevels", result.updateBidLevels());
        line.put("updateAskLevels", result.updateAskLevels());
        line.put("sequence", result.sequence());
        line.put("checksum", result.checksum());
        line.put("loadMillis", result.loadMillis());
        line.put("rawBytes", result.rawBytes());
        line.put("requiresAuthentication", result.requiresAuthentication());
        line.put("qualityCheckedMessages", result.qualityCheckedMessages());
        line.put("qualityChecksPassed", result.qualityChecksPassed());
        line.put("qualityChecksFailed", result.qualityChecksFailed());
        line.put("qualityPassed", result.qualityPassed());
        line.put("qualityFailures", result.qualityFailures());
        line.put("qualitySequence", result.qualitySequence());
        line.put("qualityChecksum", result.qualityChecksum());
        line.put("message", result.message());
        return mapper.writeValueAsString(line);
    }

    private static long elapsedMillis(long startNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
    }

    private static String runId() {
        return Instant.now().toString().replace(":", "").replace(".", "");
    }

    private static final class BookMessageCapture implements WebSocket.Listener {
        private final ObjectMapper mapper;
        private final DeepBookSourceDefinition source;
        private final List<String> payloads = new CopyOnWriteArrayList<>();
        private final AtomicReference<Throwable> failure = new AtomicReference<>();
        private final Object monitor = new Object();
        private final StringBuilder buffer = new StringBuilder();
        private volatile WebSocket webSocket;
        private volatile boolean opened;

        BookMessageCapture(ObjectMapper mapper, DeepBookSourceDefinition source) {
            this.mapper = mapper;
            this.source = source;
        }

        @Override
        public void onOpen(WebSocket openedWebSocket) {
            webSocket = openedWebSocket;
            opened = true;
            if (source.hasSubscribeMessage()) {
                openedWebSocket.sendText(source.subscribeMessage(), true);
            }
            synchronized (monitor) {
                monitor.notifyAll();
            }
            openedWebSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket currentWebSocket, CharSequence data, boolean last) {
            buffer.append(data);
            if (!last) {
                currentWebSocket.request(1);
                return null;
            }

            String raw = buffer.toString();
            buffer.setLength(0);
            try {
                JsonNode root = mapper.readTree(raw);
                if (isBookPayload(source, root)) {
                    payloads.add(raw);
                    synchronized (monitor) {
                        monitor.notifyAll();
                    }
                }
            } catch (Exception ignored) {
                // Subscription acknowledgements and control messages are not market data.
            }
            currentWebSocket.request(1);
            return null;
        }

        @Override
        public void onError(WebSocket currentWebSocket, Throwable error) {
            failure.compareAndSet(null, error);
            synchronized (monitor) {
                monitor.notifyAll();
            }
        }

        void awaitOpen(Duration timeout) throws Exception {
            long deadline = System.nanoTime() + timeout.toNanos();
            synchronized (monitor) {
                while (!opened && failure.get() == null) {
                    waitForChange(deadline - System.nanoTime());
                    if (System.nanoTime() >= deadline) {
                        break;
                    }
                }
            }
            requireHealthy();
            if (!opened) {
                throw new IllegalStateException("timed out opening deep-book websocket for " + source.id());
            }
        }

        List<String> awaitMessages(int count, Duration timeout) throws Exception {
            long deadline = System.nanoTime() + timeout.toNanos();
            synchronized (monitor) {
                while (payloads.size() < count && failure.get() == null) {
                    waitForChange(deadline - System.nanoTime());
                    if (System.nanoTime() >= deadline) {
                        break;
                    }
                }
            }
            requireHealthy();
            if (payloads.size() < count) {
                throw new IllegalStateException("received " + payloads.size() + " of " + count
                        + " required deep-book messages from " + source.id());
            }
            return List.copyOf(payloads.subList(0, count));
        }

        void awaitChange(int previousCount, long remainingNanos) throws Exception {
            synchronized (monitor) {
                if (payloads.size() == previousCount && failure.get() == null && remainingNanos > 0) {
                    waitForChange(remainingNanos);
                }
            }
            requireHealthy();
        }

        List<String> payloads() {
            return List.copyOf(payloads);
        }

        void close() {
            WebSocket current = webSocket;
            if (current != null) {
                current.abort();
            }
        }

        private void requireHealthy() {
            Throwable error = failure.get();
            if (error != null) {
                throw new IllegalStateException("deep-book websocket failed for " + source.id(), error);
            }
        }

        private void waitForChange(long remainingNanos) throws InterruptedException {
            if (remainingNanos <= 0) {
                return;
            }
            long millis = TimeUnit.NANOSECONDS.toMillis(remainingNanos);
            int nanos = (int) (remainingNanos - TimeUnit.MILLISECONDS.toNanos(millis));
            monitor.wait(Math.max(0, millis), Math.max(0, nanos));
        }
    }

    private record DeepBookProbeResult(
            String sourceId,
            String exchange,
            String symbol,
            String channel,
            int configuredDepthLevels,
            boolean requiresAuthentication,
            boolean transportSuccess,
            boolean qualityAccepted,
            int snapshotBidLevels,
            int snapshotAskLevels,
            int updateBidLevels,
            int updateAskLevels,
            String sequence,
            String checksum,
            long loadMillis,
            long rawBytes,
            int qualityCheckedMessages,
            int qualityChecksPassed,
            int qualityChecksFailed,
            String qualityPassed,
            String qualityFailures,
            String qualitySequence,
            String qualityChecksum,
            String message
    ) {
        static DeepBookProbeResult connected(
                DeepBookSourceDefinition source,
                long loadMillis,
                int snapshotBidLevels,
                int snapshotAskLevels,
                int updateBidLevels,
                int updateAskLevels,
                String sequence,
                String checksum,
                long rawBytes,
                String message,
                DeepBookQualityReport quality
        ) {
            return new DeepBookProbeResult(
                    source.id(),
                    source.exchange(),
                    source.symbol(),
                    source.channel(),
                    source.depthLevels(),
                    source.requiresAuthentication(),
                    true,
                    quality.accepted(),
                    snapshotBidLevels,
                    snapshotAskLevels,
                    updateBidLevels,
                    updateAskLevels,
                    sequence,
                    checksum,
                    loadMillis,
                    rawBytes,
                    quality.checkedMessages(),
                    quality.passedCount(),
                    quality.failedCount(),
                    quality.passedSummary(),
                    quality.failureSummary(),
                    quality.sequenceDetails(),
                    quality.checksumDetails(),
                    message
            );
        }

        static DeepBookProbeResult failure(DeepBookSourceDefinition source, long loadMillis, String message) {
            return new DeepBookProbeResult(
                    source.id(),
                    source.exchange(),
                    source.symbol(),
                    source.channel(),
                    source.depthLevels(),
                    source.requiresAuthentication(),
                    false,
                    false,
                    0,
                    0,
                    0,
                    0,
                    "",
                    "",
                    loadMillis,
                    0,
                    0,
                    0,
                    1,
                    "",
                    "transport:" + message,
                    "",
                    "",
                    message
            );
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
                    + " connected=" + transportSuccess
                    + " qualityAccepted=" + qualityAccepted
                    + " snapshotBidLevels=" + snapshotBidLevels
                    + " snapshotAskLevels=" + snapshotAskLevels
                    + " updateBidLevels=" + updateBidLevels
                    + " updateAskLevels=" + updateAskLevels
                    + " sequence=" + sequence.replace(' ', '_')
                    + " checksum=" + checksum
                    + " qualityPassed=" + qualityChecksPassed
                    + " qualityFailed=" + qualityChecksFailed
                    + " qualityFailures=" + qualityFailures.replace(' ', '_')
                    + " loadMs=" + String.format(Locale.ROOT, "%d", loadMillis)
                    + " rawBytes=" + rawBytes
                    + " message=" + message.replace(' ', '_');
        }
    }
}

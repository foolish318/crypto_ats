package com.example.hft.app;

import com.example.hft.datasource.DataSourceModuleVersion;
import com.example.hft.datasource.book.DepthUpdateApplyResult;
import com.example.hft.datasource.book.SequencedLocalOrderBook;
import com.example.hft.exchange.binance.BinanceBookTickerSource;
import com.example.hft.exchange.binance.BinanceDepthParser;
import com.example.hft.marketdata.model.DepthBookTop;
import com.example.hft.marketdata.model.DepthUpdate;
import com.example.hft.marketdata.model.RawDepthPayload;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;


public final class BinanceRawDepthOrderBookMain {
    private static final List<String> DEFAULT_SYMBOLS = List.of("BTCUSDT", "ETHUSDT", "BNBUSDT", "SOLUSDT", "XRPUSDT");
    private static final int DEFAULT_DURATION_SECONDS = 1_800;
    private static final int DEFAULT_LEVELS = 10;
    private static final int SNAPSHOT_LIMIT = 5_000;
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration SNAPSHOT_TIMEOUT = Duration.ofSeconds(10);
    private static final String EXCHANGE = "BINANCE_US";
    private static final String SNAPSHOT_URI = "https://api.binance.us/api/v3/depth?symbol=%s&limit=%d";

    private BinanceRawDepthOrderBookMain() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && "replay".equalsIgnoreCase(args[0])) {
            if (args.length < 3) {
                throw new IllegalArgumentException("replay mode requires rawFile and snapshotFile");
            }
            int levels = args.length > 3 ? Integer.parseInt(args[3]) : DEFAULT_LEVELS;
            replay(Path.of(args[1]), Path.of(args[2]), levels);
            return;
        }

        int offset = args.length > 0 && "record".equalsIgnoreCase(args[0]) ? 1 : 0;
        int durationSeconds = args.length > offset ? Integer.parseInt(args[offset]) : DEFAULT_DURATION_SECONDS;
        List<String> symbols = args.length > offset + 1 ? parseSymbols(args[offset + 1]) : DEFAULT_SYMBOLS;
        int levels = args.length > offset + 2 ? Integer.parseInt(args[offset + 2]) : DEFAULT_LEVELS;
        Path outputDir = args.length > offset + 3 ? Path.of(args[offset + 3]) : Path.of("data");
        record(durationSeconds, symbols, levels, outputDir);
    }

    private static void record(int durationSeconds, List<String> symbols, int levels, Path outputDir) throws Exception {
        Files.createDirectories(outputDir);
        ObjectMapper mapper = new ObjectMapper();
        String runId = runId();
        Path rawFile = outputDir.resolve("binance-raw-depth-v18-" + runId + ".jsonl");
        Path snapshotFile = outputDir.resolve("binance-depth-snapshots-v18-" + runId + ".jsonl");
        Path bookEventFile = outputDir.resolve("binance-book-events-v18-" + runId + ".jsonl");
        Path summaryFile = outputDir.resolve("binance-book-summary-v18-" + runId + ".json");

        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
        LinkedBlockingQueue<RawDepthPayload> rawQueue = new LinkedBlockingQueue<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicBoolean stopping = new AtomicBoolean();
        AtomicLong sequence = new AtomicLong();
        URI uri = URI.create(BinanceBookTickerSource.BINANCE_US_STREAM_URI + streamNames(symbols));
        WebSocket webSocket = connectDepthStream(httpClient, uri, rawQueue, sequence, stopping, failure);

        Map<String, SequencedLocalOrderBook> books = loadSnapshots(httpClient, symbols, snapshotFile, mapper, "INITIAL");
        BinanceDepthParser parser = new BinanceDepthParser();
        RawDepthBookStats stats = new RawDepthBookStats(symbols);
        long startedNanos = System.nanoTime();
        long deadlineNanos = startedNanos + TimeUnit.SECONDS.toNanos(durationSeconds);

        try (BufferedWriter rawWriter = Files.newBufferedWriter(rawFile, StandardCharsets.UTF_8);
             BufferedWriter bookWriter = Files.newBufferedWriter(bookEventFile, StandardCharsets.UTF_8)) {
            while (System.nanoTime() < deadlineNanos) {
                RawDepthPayload raw = rawQueue.poll(100, TimeUnit.MILLISECONDS);
                if (raw == null) {
                    if (failure.get() != null) {
                        webSocket = reconnectDepthStream(webSocket, httpClient, uri, rawQueue, sequence, stopping, failure, stats);
                        resyncAllBooks(httpClient, books, symbols, snapshotFile, mapper, stats, "WEBSOCKET_RECONNECT");
                    }
                    continue;
                }
                processRaw(raw, books, parser, stats, mapper, rawWriter, bookWriter, levels, httpClient, snapshotFile);
            }

            stopping.set(true);
            webSocket.abort();
            RawDepthPayload raw;
            while ((raw = rawQueue.poll()) != null) {
                processRaw(raw, books, parser, stats, mapper, rawWriter, bookWriter, levels, httpClient, snapshotFile);
            }
        } finally {
            stopping.set(true);
            webSocket.abort();
        }

        long elapsedNanos = System.nanoTime() - startedNanos;
        writeSummary(summaryFile, mapper, stats, books, durationSeconds, elapsedNanos, rawFile, snapshotFile,
                bookEventFile, levels, "record");
        printSummary(stats, books, durationSeconds, elapsedNanos, rawFile, snapshotFile, bookEventFile, summaryFile,
                "BINANCE_RAW_DEPTH_BOOK_SUMMARY");
    }
    private static WebSocket connectDepthStream(HttpClient httpClient, URI uri, LinkedBlockingQueue<RawDepthPayload> rawQueue,
                                                AtomicLong sequence, AtomicBoolean stopping,
                                                AtomicReference<Throwable> failure) throws Exception {
        CountDownLatch open = new CountDownLatch(1);
        WebSocket webSocket = httpClient.newWebSocketBuilder()
                .header("User-Agent", "hft-java-learning/0.1")
                .buildAsync(uri, new WebSocket.Listener() {
                    private final StringBuilder buffer = new StringBuilder();

                    @Override
                    public void onOpen(WebSocket webSocket) {
                        open.countDown();
                        webSocket.request(1);
                    }

                    @Override
                    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                        long localReceivedEpochMillis = System.currentTimeMillis();
                        long rawReceivedNanos = System.nanoTime();
                        buffer.append(data);
                        if (!last) {
                            webSocket.request(1);
                            return null;
                        }

                        String payload = buffer.toString();
                        buffer.setLength(0);
                        rawQueue.add(new RawDepthPayload(sequence.incrementAndGet(), extractSymbol(payload),
                                localReceivedEpochMillis, rawReceivedNanos, payload));
                        webSocket.request(1);
                        return null;
                    }

                    @Override
                    public void onError(WebSocket webSocket, Throwable error) {
                        if (!stopping.get()) {
                            failure.compareAndSet(null, error);
                        }
                    }
                })
                .join();

        if (!open.await(CONNECT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
            webSocket.abort();
            throw new IllegalStateException("timed out opening Binance.US depth stream " + uri);
        }
        failure.set(null);
        return webSocket;
    }

    private static WebSocket reconnectDepthStream(WebSocket current, HttpClient httpClient, URI uri,
                                                  LinkedBlockingQueue<RawDepthPayload> rawQueue, AtomicLong sequence,
                                                  AtomicBoolean stopping, AtomicReference<Throwable> failure,
                                                  RawDepthBookStats stats) throws Exception {
        stats.addReconnectAttempt();
        current.abort();
        try {
            WebSocket replacement = connectDepthStream(httpClient, uri, rawQueue, sequence, stopping, failure);
            stats.addReconnectSuccess();
            return replacement;
        } catch (Exception e) {
            stats.addReconnectFailure();
            failure.compareAndSet(null, e);
            throw e;
        }
    }

    private static void resyncAllBooks(HttpClient httpClient, Map<String, SequencedLocalOrderBook> books,
                                       List<String> symbols, Path snapshotFile, ObjectMapper mapper,
                                       RawDepthBookStats stats, String reason) throws Exception {
        for (String symbol : symbols) {
            SequencedLocalOrderBook book = books.get(symbol.toUpperCase(Locale.ROOT));
            if (book != null) {
                resyncBook(httpClient, book, snapshotFile, mapper, stats, reason);
            }
        }
    }

    private static boolean resyncBook(HttpClient httpClient, SequencedLocalOrderBook book, Path snapshotFile,
                                      ObjectMapper mapper, RawDepthBookStats stats, String reason) {
        stats.addResyncAttempt(book.symbol());
        try {
            SnapshotPayload snapshot = fetchSnapshot(httpClient, book.symbol());
            book.loadSnapshot(snapshot.payload());
            try (BufferedWriter writer = Files.newBufferedWriter(snapshotFile, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                writeSnapshotLine(writer, mapper, book.symbol(), snapshot.receivedEpochMillis(), book.lastUpdateId(),
                        snapshot.payload(), reason);
            }
            stats.addResyncSuccess(book.symbol());
            return true;
        } catch (Exception e) {
            stats.addResyncFailure(book.symbol());
            return false;
        }
    }

    private static SnapshotPayload fetchSnapshot(HttpClient httpClient, String symbol) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(String.format(SNAPSHOT_URI, symbol, SNAPSHOT_LIMIT)))
                .timeout(SNAPSHOT_TIMEOUT)
                .GET()
                .build();
        long receivedEpochMillis = System.currentTimeMillis();
        String payload = httpClient.send(request, HttpResponse.BodyHandlers.ofString()).body();
        return new SnapshotPayload(receivedEpochMillis, payload);
    }
    private static void replay(Path rawFile, Path snapshotFile, int levels) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Map<String, SequencedLocalOrderBook> books = loadSnapshotsFromFile(snapshotFile, mapper);
        BinanceDepthParser parser = new BinanceDepthParser();
        RawDepthBookStats stats = new RawDepthBookStats(new ArrayList<>(books.keySet()));
        Path summaryFile = rawFile.getParent() == null
                ? Path.of("binance-book-replay-summary-v18-" + runId() + ".json")
                : rawFile.getParent().resolve("binance-book-replay-summary-v18-" + runId() + ".json");
        long startedNanos = System.nanoTime();

        try (BufferedReader reader = Files.newBufferedReader(rawFile, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                JsonNode root = mapper.readTree(line);
                RawDepthPayload raw = new RawDepthPayload(
                        root.get("sequenceNumber").asLong(),
                        root.path("routingSymbol").asText(""),
                        root.get("localReceivedEpochMillis").asLong(),
                        0L,
                        root.get("rawPayload").asText());
                processRaw(raw, books, parser, stats, mapper, null, null, levels, null, null);
            }
        }

        long elapsedNanos = System.nanoTime() - startedNanos;
        writeSummary(summaryFile, mapper, stats, books, 0, elapsedNanos, rawFile, snapshotFile, null, levels, "replay");
        printSummary(stats, books, 0, elapsedNanos, rawFile, snapshotFile, null, summaryFile,
                "BINANCE_RAW_DEPTH_REPLAY_SUMMARY");
    }

    private static Map<String, SequencedLocalOrderBook> loadSnapshots(HttpClient httpClient, List<String> symbols,
                                                                       Path snapshotFile, ObjectMapper mapper,
                                                                       String reason)
            throws Exception {
        Map<String, SequencedLocalOrderBook> books = new LinkedHashMap<>();
        try (BufferedWriter writer = Files.newBufferedWriter(snapshotFile, StandardCharsets.UTF_8)) {
            for (String symbol : symbols) {
                String normalized = symbol.toUpperCase(Locale.ROOT);
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(String.format(SNAPSHOT_URI, normalized, SNAPSHOT_LIMIT)))
                        .timeout(SNAPSHOT_TIMEOUT)
                        .GET()
                        .build();
                long receivedEpochMillis = System.currentTimeMillis();
                String payload = httpClient.send(request, HttpResponse.BodyHandlers.ofString()).body();
                SequencedLocalOrderBook book = new SequencedLocalOrderBook(normalized);
                book.loadSnapshot(payload);
                books.put(normalized, book);
                writeSnapshotLine(writer, mapper, normalized, receivedEpochMillis, book.lastUpdateId(), payload, reason);
            }
        }
        return books;
    }

    private static Map<String, SequencedLocalOrderBook> loadSnapshotsFromFile(Path snapshotFile, ObjectMapper mapper)
            throws Exception {
        Map<String, SequencedLocalOrderBook> books = new LinkedHashMap<>();
        try (BufferedReader reader = Files.newBufferedReader(snapshotFile, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                JsonNode root = mapper.readTree(line);
                String symbol = root.get("symbol").asText().toUpperCase(Locale.ROOT);
                SequencedLocalOrderBook book = new SequencedLocalOrderBook(symbol);
                book.loadSnapshot(root.get("rawPayload").asText());
                books.put(symbol, book);
            }
        }
        return books;
    }

    private static void processRaw(RawDepthPayload raw, Map<String, SequencedLocalOrderBook> books,
                                   BinanceDepthParser parser, RawDepthBookStats stats, ObjectMapper mapper,
                                   BufferedWriter rawWriter, BufferedWriter bookWriter, int levels,
                                   HttpClient httpClient, Path snapshotFile) throws Exception {
        if (rawWriter != null) {
            writeRawLine(rawWriter, mapper, raw);
        }
        stats.addRaw(raw.routingSymbol());

        long parseStartNanos = System.nanoTime();
        DepthUpdate update;
        try {
            update = parser.parseUpdate(raw.rawPayload());
        } catch (Exception e) {
            stats.addParseFailure(raw.routingSymbol());
            if (bookWriter != null) {
                writeBookEventLine(bookWriter, mapper, raw, null, null, null, "PARSE_FAILED", 0L, 0L, levels,
                        "UNKNOWN");
            }
            return;
        }
        long parsedNanos = System.nanoTime();

        SequencedLocalOrderBook book = books.get(update.symbol());
        long bookStartNanos = System.nanoTime();
        DepthUpdateApplyResult result = book == null ? DepthUpdateApplyResult.UNKNOWN_SYMBOL : book.apply(update);
        DepthBookTop top = book == null ? null : book.topLevels(levels);
        long bookEndNanos = System.nanoTime();
        long parseNanos = parsedNanos - parseStartNanos;
        long bookNanos = bookEndNanos - bookStartNanos;
        long localE2eNanos = raw.rawReceivedNanos() == 0L ? 0L : bookEndNanos - raw.rawReceivedNanos();
        long exchangeToReceiveNanos = (raw.localReceivedEpochMillis() - update.exchangeEventTimeMillis()) * 1_000_000L;
        stats.record(update.symbol(), result, parseNanos, bookNanos, localE2eNanos, exchangeToReceiveNanos);

        if (bookWriter != null) {
            String quality = book == null ? "UNKNOWN" : book.quality().name();
            writeBookEventLine(bookWriter, mapper, raw, update, book, top, result.name(), parseNanos, bookNanos,
                    levels, quality);
        }
        if (httpClient != null && snapshotFile != null && book != null && needsResync(result)) {
            resyncBook(httpClient, book, snapshotFile, mapper, stats, result.name());
        }
    }

    private static boolean needsResync(DepthUpdateApplyResult result) {
        return result == DepthUpdateApplyResult.GAP || result == DepthUpdateApplyResult.CROSSED;
    }
    private static void writeRawLine(BufferedWriter writer, ObjectMapper mapper, RawDepthPayload raw) throws Exception {
        ObjectNode line = mapper.createObjectNode();
        line.put("version", DataSourceModuleVersion.VERSION);
        line.put("exchange", EXCHANGE);
        line.put("sequenceNumber", raw.sequenceNumber());
        line.put("routingSymbol", raw.routingSymbol());
        line.put("localReceivedEpochMillis", raw.localReceivedEpochMillis());
        line.put("rawReceivedNanos", raw.rawReceivedNanos());
        line.put("rawPayload", raw.rawPayload());
        writer.write(mapper.writeValueAsString(line));
        writer.newLine();
    }

    private static void writeSnapshotLine(BufferedWriter writer, ObjectMapper mapper, String symbol,
                                          long receivedEpochMillis, long lastUpdateId, String payload,
                                          String reason) throws Exception {
        ObjectNode line = mapper.createObjectNode();
        line.put("version", DataSourceModuleVersion.VERSION);
        line.put("exchange", EXCHANGE);
        line.put("symbol", symbol);
        line.put("snapshotReceivedEpochMillis", receivedEpochMillis);
        line.put("snapshotLimit", SNAPSHOT_LIMIT);
        line.put("lastUpdateId", lastUpdateId);
        line.put("reason", reason);
        line.put("rawPayload", payload);
        writer.write(mapper.writeValueAsString(line));
        writer.newLine();
    }
    private static void writeBookEventLine(BufferedWriter writer, ObjectMapper mapper, RawDepthPayload raw,
                                           DepthUpdate update, SequencedLocalOrderBook book, DepthBookTop top,
                                           String result, long parseNanos, long bookNanos, int levels, String quality)
            throws Exception {
        ObjectNode line = mapper.createObjectNode();
        line.put("version", DataSourceModuleVersion.VERSION);
        line.put("exchange", EXCHANGE);
        line.put("sequenceNumber", raw.sequenceNumber());
        line.put("routingSymbol", raw.routingSymbol());
        line.put("symbol", update == null ? raw.routingSymbol() : update.symbol());
        line.put("applyResult", result);
        line.put("quality", quality);
        line.put("localReceivedEpochMillis", raw.localReceivedEpochMillis());
        if (update != null) {
            line.put("exchangeEventTimeMillis", update.exchangeEventTimeMillis());
            line.put("firstUpdateId", update.firstUpdateId());
            line.put("finalUpdateId", update.finalUpdateId());
        }
        if (book != null) {
            line.put("lastUpdateId", book.lastUpdateId());
        }
        line.put("parseNanos", parseNanos);
        line.put("bookNanos", bookNanos);
        if (top != null) {
            addTopLevels(line, top, levels);
        }
        writer.write(mapper.writeValueAsString(line));
        writer.newLine();
    }

    private static void writeSummary(Path summaryFile, ObjectMapper mapper, RawDepthBookStats stats,
                                     Map<String, SequencedLocalOrderBook> books, int durationSeconds,
                                     long elapsedNanos, Path rawFile, Path snapshotFile, Path bookEventFile,
                                     int levels, String mode) throws Exception {
        ObjectNode root = mapper.createObjectNode();
        root.put("version", DataSourceModuleVersion.VERSION);
        root.put("mode", mode);
        root.put("exchange", EXCHANGE);
        root.put("durationSeconds", durationSeconds);
        root.put("elapsedMillis", elapsedNanos / 1_000_000L);
        root.put("rawMessages", stats.rawMessages);
        root.put("parsed", stats.parsed);
        root.put("parseFailures", stats.parseFailures);
        root.put("applied", stats.applied);
        root.put("stale", stats.stale);
        root.put("gaps", stats.gaps);
        root.put("crossed", stats.crossed);
        root.put("unknownSymbol", stats.unknownSymbol);
        root.put("resyncAttempts", stats.resyncAttempts);
        root.put("resyncSuccesses", stats.resyncSuccesses);
        root.put("resyncFailures", stats.resyncFailures);
        root.put("reconnectAttempts", stats.reconnectAttempts);
        root.put("reconnectSuccesses", stats.reconnectSuccesses);
        root.put("reconnectFailures", stats.reconnectFailures);
        root.put("parseAvgUs", micros(stats.average(stats.parseLatencies)));
        root.put("parseP99Us", micros(stats.percentile(stats.parseLatencies, 0.99)));
        root.put("bookAvgUs", micros(stats.average(stats.bookLatencies)));
        root.put("bookP99Us", micros(stats.percentile(stats.bookLatencies, 0.99)));
        root.put("localE2EAvgUs", micros(stats.average(stats.localE2eLatencies)));
        root.put("localE2EP99Us", micros(stats.percentile(stats.localE2eLatencies, 0.99)));
        root.put("exchangeToReceiveAvgUs", micros(stats.average(stats.exchangeToReceiveLatencies)));
        root.put("exchangeToReceiveP99Us", micros(stats.percentile(stats.exchangeToReceiveLatencies, 0.99)));
        root.put("rawFile", rawFile.toString());
        root.put("snapshotFile", snapshotFile.toString());
        if (bookEventFile != null) {
            root.put("bookEventFile", bookEventFile.toString());
        }

        ArrayNode symbols = root.putArray("symbols");
        for (SequencedLocalOrderBook book : books.values()) {
            ObjectNode symbol = symbols.addObject();
            RawDepthBookStats.SymbolStats symbolStats = stats.symbolStats(book.symbol());
            symbol.put("symbol", book.symbol());
            symbol.put("quality", book.quality().name());
            symbol.put("snapshotLastUpdateId", book.snapshotLastUpdateId());
            symbol.put("lastUpdateId", book.lastUpdateId());
            symbol.put("rawMessages", symbolStats.rawMessages);
            symbol.put("applied", book.applied());
            symbol.put("stale", book.stale());
            symbol.put("gaps", book.gaps());
            symbol.put("crossed", book.crossed());
            symbol.put("unknownSymbol", book.unknownSymbol());
            symbol.put("resyncAttempts", symbolStats.resyncAttempts);
            symbol.put("resyncSuccesses", symbolStats.resyncSuccesses);
            symbol.put("resyncFailures", symbolStats.resyncFailures);
            addTopLevels(symbol, book.topLevels(levels), levels);
        }
        Files.writeString(summaryFile, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root),
                StandardCharsets.UTF_8);
    }

    private static void printSummary(RawDepthBookStats stats, Map<String, SequencedLocalOrderBook> books,
                                     int durationSeconds, long elapsedNanos, Path rawFile, Path snapshotFile,
                                     Path bookEventFile, Path summaryFile, String label) {
        System.out.println(label
                + " version=" + DataSourceModuleVersion.VERSION
                + " durationSeconds=" + durationSeconds
                + " elapsedMs=" + (elapsedNanos / 1_000_000L)
                + " rawMessages=" + stats.rawMessages
                + " parsed=" + stats.parsed
                + " parseFailures=" + stats.parseFailures
                + " applied=" + stats.applied
                + " stale=" + stats.stale
                + " gaps=" + stats.gaps
                + " crossed=" + stats.crossed
                + " unknownSymbol=" + stats.unknownSymbol
                + " resyncAttempts=" + stats.resyncAttempts
                + " resyncSuccesses=" + stats.resyncSuccesses
                + " resyncFailures=" + stats.resyncFailures
                + " reconnectAttempts=" + stats.reconnectAttempts
                + " reconnectSuccesses=" + stats.reconnectSuccesses
                + " reconnectFailures=" + stats.reconnectFailures
                + " parseAvgUs=" + micros(stats.average(stats.parseLatencies))
                + " parseP99Us=" + micros(stats.percentile(stats.parseLatencies, 0.99))
                + " bookAvgUs=" + micros(stats.average(stats.bookLatencies))
                + " bookP99Us=" + micros(stats.percentile(stats.bookLatencies, 0.99))
                + " localE2EAvgUs=" + micros(stats.average(stats.localE2eLatencies))
                + " localE2EP99Us=" + micros(stats.percentile(stats.localE2eLatencies, 0.99))
                + " exchangeToReceiveAvgUs=" + micros(stats.average(stats.exchangeToReceiveLatencies))
                + " exchangeToReceiveP99Us=" + micros(stats.percentile(stats.exchangeToReceiveLatencies, 0.99)));
        for (SequencedLocalOrderBook book : books.values()) {
            DepthBookTop top = book.topLevels(DEFAULT_LEVELS);
            System.out.println("BOOK_STATE symbol=" + book.symbol()
                    + " quality=" + book.quality()
                    + " snapshotLastUpdateId=" + book.snapshotLastUpdateId()
                    + " lastUpdateId=" + book.lastUpdateId()
                    + " applied=" + book.applied()
                    + " stale=" + book.stale()
                    + " gaps=" + book.gaps()
                    + " crossed=" + book.crossed()
                    + " resyncAttempts=" + stats.symbolStats(book.symbol()).resyncAttempts
                    + " resyncSuccesses=" + stats.symbolStats(book.symbol()).resyncSuccesses
                    + " resyncFailures=" + stats.symbolStats(book.symbol()).resyncFailures
                    + " bid1Ticks=" + top.bidPrices()[0]
                    + " ask1Ticks=" + top.askPrices()[0]
                    + " spreadTicks=" + (top.askPrices()[0] - top.bidPrices()[0]));
        }
        System.out.println("FILES raw=" + rawFile + " snapshots=" + snapshotFile
                + (bookEventFile == null ? "" : " bookEvents=" + bookEventFile)
                + " summary=" + summaryFile);
    }

    private static void addTopLevels(ObjectNode node, DepthBookTop top, int levels) {
        ArrayNode bids = node.putArray("bids");
        ArrayNode asks = node.putArray("asks");
        for (int i = 0; i < levels; i++) {
            ObjectNode bid = bids.addObject();
            bid.put("priceTicks", top.bidPrices()[i]);
            bid.put("size", top.bidSizes()[i]);
            ObjectNode ask = asks.addObject();
            ask.put("priceTicks", top.askPrices()[i]);
            ask.put("size", top.askSizes()[i]);
        }
        node.put("bid5Volume", volume(top.bidSizes(), 5));
        node.put("ask5Volume", volume(top.askSizes(), 5));
        node.put("bid10Volume", volume(top.bidSizes(), 10));
        node.put("ask10Volume", volume(top.askSizes(), 10));
        node.put("spreadTicks", top.askPrices()[0] - top.bidPrices()[0]);
    }

    private static long volume(int[] sizes, int levels) {
        long total = 0;
        for (int i = 0; i < Math.min(levels, sizes.length); i++) {
            total += sizes[i];
        }
        return total;
    }
    private static String streamNames(List<String> symbols) {
        List<String> streams = new ArrayList<>(symbols.size());
        for (String symbol : symbols) {
            streams.add(symbol.toLowerCase(Locale.ROOT) + "@depth@100ms");
        }
        return String.join("/", streams);
    }

    private static List<String> parseSymbols(String value) {
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(symbol -> !symbol.isEmpty())
                .map(symbol -> symbol.toUpperCase(Locale.ROOT))
                .toList();
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

    private static String runId() {
        return Instant.now().toString().replace(":", "").replace(".", "");
    }

    private static String micros(double nanos) {
        return String.format(Locale.ROOT, "%.2f", nanos / 1_000.0);
    }

    private record SnapshotPayload(long receivedEpochMillis, String payload) {
    }

    private static final class RawDepthBookStats {
        private final Map<String, SymbolStats> bySymbol = new LinkedHashMap<>();
        private final List<Long> parseLatencies = new ArrayList<>();
        private final List<Long> bookLatencies = new ArrayList<>();
        private final List<Long> localE2eLatencies = new ArrayList<>();
        private final List<Long> exchangeToReceiveLatencies = new ArrayList<>();

        private long rawMessages;
        private long parsed;
        private long parseFailures;
        private long applied;
        private long stale;
        private long gaps;
        private long crossed;
        private long unknownSymbol;
        private long resyncAttempts;
        private long resyncSuccesses;
        private long resyncFailures;
        private long reconnectAttempts;
        private long reconnectSuccesses;
        private long reconnectFailures;

        private RawDepthBookStats(List<String> symbols) {
            for (String symbol : symbols) {
                bySymbol.put(symbol.toUpperCase(Locale.ROOT), new SymbolStats());
            }
        }

        private void addRaw(String symbol) {
            rawMessages++;
            symbolStats(symbol).rawMessages++;
        }

        private void addParseFailure(String symbol) {
            parseFailures++;
            symbolStats(symbol).parseFailures++;
        }

        private void addResyncAttempt(String symbol) {
            resyncAttempts++;
            symbolStats(symbol).resyncAttempts++;
        }

        private void addResyncSuccess(String symbol) {
            resyncSuccesses++;
            symbolStats(symbol).resyncSuccesses++;
        }

        private void addResyncFailure(String symbol) {
            resyncFailures++;
            symbolStats(symbol).resyncFailures++;
        }

        private void addReconnectAttempt() {
            reconnectAttempts++;
        }

        private void addReconnectSuccess() {
            reconnectSuccesses++;
        }

        private void addReconnectFailure() {
            reconnectFailures++;
        }
        private void record(String symbol, DepthUpdateApplyResult result, long parseNanos, long bookNanos,
                            long localE2eNanos, long exchangeToReceiveNanos) {
            parsed++;
            SymbolStats symbolStats = symbolStats(symbol);
            symbolStats.parsed++;
            parseLatencies.add(parseNanos);
            bookLatencies.add(bookNanos);
            if (localE2eNanos > 0L) {
                localE2eLatencies.add(localE2eNanos);
            }
            exchangeToReceiveLatencies.add(exchangeToReceiveNanos);

            switch (result) {
                case APPLIED -> {
                    applied++;
                    symbolStats.applied++;
                }
                case STALE -> {
                    stale++;
                    symbolStats.stale++;
                }
                case GAP -> {
                    gaps++;
                    symbolStats.gaps++;
                }
                case CROSSED -> {
                    crossed++;
                    symbolStats.crossed++;
                }
                case UNKNOWN_SYMBOL -> {
                    unknownSymbol++;
                    symbolStats.unknownSymbol++;
                }
            }
        }

        private SymbolStats symbolStats(String symbol) {
            String normalized = symbol == null || symbol.isBlank() ? "UNKNOWN" : symbol.toUpperCase(Locale.ROOT);
            return bySymbol.computeIfAbsent(normalized, ignored -> new SymbolStats());
        }

        private double average(List<Long> values) {
            if (values.isEmpty()) {
                return 0.0;
            }
            long total = 0L;
            for (long value : values) {
                total += value;
            }
            return (double) total / values.size();
        }

        private double percentile(List<Long> values, double percentile) {
            if (values.isEmpty()) {
                return 0.0;
            }
            List<Long> copy = new ArrayList<>(values);
            Collections.sort(copy);
            int index = (int) Math.ceil(copy.size() * percentile) - 1;
            return copy.get(Math.max(0, Math.min(index, copy.size() - 1)));
        }

        private static final class SymbolStats {
            private long rawMessages;
            private long parsed;
            private long parseFailures;
            private long applied;
            private long stale;
            private long gaps;
            private long crossed;
            private long unknownSymbol;
            private long resyncAttempts;
            private long resyncSuccesses;
            private long resyncFailures;

        }
    }
}
package com.example.hft.app;

import com.example.hft.exchange.binance.BinanceBookTickerSource;
import com.example.hft.exchange.binance.BinanceDepthParser;
import com.example.hft.marketdata.model.DepthBookTop;
import com.example.hft.marketdata.model.DepthLatencyEnvelope;
import com.example.hft.marketdata.model.DepthMarketDataRecord;
import com.example.hft.marketdata.model.DepthUpdate;
import com.example.hft.marketdata.model.LocalOrderBook;
import com.example.hft.marketdata.model.TradingSignal;
import com.example.hft.pipeline.DepthLatencyStats;
import com.example.hft.strategy.DepthMarketDataProcessor;
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
import org.jctools.queues.SpscArrayQueue;





public final class BinanceDepthLatencyMain {
    private static final List<String> DEFAULT_SYMBOLS = List.of("BTCUSDT", "ETHUSDT", "BNBUSDT", "SOLUSDT", "XRPUSDT");
    private static final int DEFAULT_LIMIT = 200;
    private static final int DEFAULT_WORKERS = 4;
    private static final int QUEUE_CAPACITY = 8_192;
    private static final int BOOK_SNAPSHOT_LIMIT = 100;
    private static final int TOP_LEVELS = 10;
    private static final Duration TIMEOUT = Duration.ofSeconds(60);
    private static final String SNAPSHOT_URI = "https://api.binance.us/api/v3/depth?symbol=%s&limit=%d";

    private BinanceDepthLatencyMain() {
    }

    public static void main(String[] args) throws Exception {
        int limit = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_LIMIT;
        List<String> symbols = args.length > 1 ? List.of(args[1].split(",")) : DEFAULT_SYMBOLS;
        int workers = args.length > 2 ? Integer.parseInt(args[2]) : DEFAULT_WORKERS;
        Path output = args.length > 3 ? Path.of(args[3]) : defaultOutputPath();

        HttpClient httpClient = HttpClient.newHttpClient();
        Map<String, LocalOrderBook> books = loadSnapshots(httpClient, symbols);
        DepthLatencyStats stats = new DepthLatencyStats("v9-depth-v5-spsc", limit);
        SpscDepthQueue queue = new SpscDepthQueue(workers, stats);
        List<DepthMarketDataRecord> records = new ArrayList<>(limit);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        queue.start();
        try {
            collectFromWebSocket(httpClient, limit, symbols, books, queue, stats, records, failure);
        } finally {
            queue.stopAndJoin();
        }

        if (failure.get() != null) {
            throw new IllegalStateException("depth latency pipeline failed", failure.get());
        }

        writeJsonLines(output, records, workers);
        System.out.println("stored actual Binance.US depth messages=" + records.size() + " file=" + output);
        System.out.println(stats.toDisplayLine());
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
                                             Map<String, LocalOrderBook> books, SpscDepthQueue queue,
                                             DepthLatencyStats stats, List<DepthMarketDataRecord> records,
                                             AtomicReference<Throwable> failure) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        BinanceDepthParser parser = new BinanceDepthParser();
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
                            DepthUpdate update = parser.parseUpdate(payload);
                            long parsedNanos = System.nanoTime();
                            LocalOrderBook book = books.get(update.symbol());
                            if (book == null) {
                                return null;
                            }
                            boolean applied = book.applyDepthUpdate(update);
                            if (!applied) {
                                return null;
                            }

                            DepthBookTop top = book.topLevels(TOP_LEVELS);
                            long bookUpdatedNanos = System.nanoTime();
                            DepthMarketDataRecord record = new DepthMarketDataRecord(++sequenceNumber, update.symbol(),
                                    update.exchangeEventTimeMillis(), localReceivedEpochMillis, rawReceivedNanos,
                                    parsedNanos, bookUpdatedNanos, top.bidPrices(), top.bidSizes(), top.askPrices(),
                                    top.askSizes(), payload);

                            long enqueueStart = System.nanoTime();
                            queue.publish(record, enqueueStart);
                            stats.addProducerOfferNanos(System.nanoTime() - enqueueStart);
                            records.add(record);
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
                        doneCollecting = true;
                        failure.compareAndSet(null, error);
                        done.countDown();
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

    private static String streamNames(List<String> symbols) {
        List<String> streams = new ArrayList<>(symbols.size());
        for (String symbol : symbols) {
            streams.add(symbol.toLowerCase(Locale.ROOT) + "@depth@100ms");
        }
        return String.join("/", streams);
    }

    private static Path defaultOutputPath() {
        return Path.of("data", "binance-depth-" + Instant.now().toString().replace(":", "") + ".jsonl");
    }

    private static void writeJsonLines(Path output, List<DepthMarketDataRecord> records, int workers) throws Exception {
        Files.createDirectories(output.getParent());
        ObjectMapper mapper = new ObjectMapper();
        try (BufferedWriter writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
            for (DepthMarketDataRecord record : records) {
                ObjectNode line = mapper.createObjectNode();
                line.put("mode", "v9-depth-v5-spsc");
                line.put("workers", workers);
                line.put("sequenceNumber", record.sequenceNumber());
                line.put("symbol", record.symbol());
                line.put("exchangeEventTimeMillis", record.exchangeEventTimeMillis());
                line.put("localReceivedEpochMillis", record.localReceivedEpochMillis());
                line.put("topSpreadTicks", record.topSpreadTicks());
                line.put("bid5Volume", record.bidVolume(5));
                line.put("ask5Volume", record.askVolume(5));
                line.put("bid10Volume", record.bidVolume(10));
                line.put("ask10Volume", record.askVolume(10));
                line.put("rawPayload", record.rawPayload());
                writer.write(mapper.writeValueAsString(line));
                writer.newLine();
            }
        }
    }

    private static final class SpscDepthQueue {
        private final SpscArrayQueue<DepthLatencyEnvelope>[] queues;
        private final List<Thread> threads = new ArrayList<>();
        private final DepthLatencyStats stats;
        private final AtomicReference<Throwable> failure = new AtomicReference<>();

        @SuppressWarnings("unchecked")
        private SpscDepthQueue(int workers, DepthLatencyStats stats) {
            this.queues = new SpscArrayQueue[workers];
            this.stats = stats;
            for (int i = 0; i < workers; i++) {
                queues[i] = new SpscArrayQueue<>(QUEUE_CAPACITY);
            }
        }

        private void start() {
            for (int i = 0; i < queues.length; i++) {
                int workerIndex = i;
                Thread worker = new Thread(() -> runWorker(queues[workerIndex]), "depth-spsc-worker-" + (workerIndex + 1));
                threads.add(worker);
                worker.start();
            }
        }

        private void publish(DepthMarketDataRecord record, long enqueuedNanos) {
            int partition = Math.floorMod(record.symbol().hashCode(), queues.length);
            offerSpinning(queues[partition], DepthLatencyEnvelope.record(record, enqueuedNanos));
        }

        private void stopAndJoin() throws InterruptedException {
            for (SpscArrayQueue<DepthLatencyEnvelope> queue : queues) {
                offerSpinning(queue, DepthLatencyEnvelope.stop());
            }
            for (Thread thread : threads) {
                thread.join();
            }
            if (failure.get() != null) {
                throw new IllegalStateException("depth worker failed", failure.get());
            }
        }

        private void runWorker(SpscArrayQueue<DepthLatencyEnvelope> queue) {
            DepthMarketDataProcessor processor = new DepthMarketDataProcessor();
            try {
                while (true) {
                    DepthLatencyEnvelope envelope = pollSpinning(queue);
                    if (envelope.isStop()) {
                        return;
                    }
                    long processingStart = System.nanoTime();
                    TradingSignal signal = processor.signalFor(envelope.record());
                    long processingEnd = System.nanoTime();
                    stats.record(envelope, processingStart, processingEnd, signal);
                }
            } catch (Throwable t) {
                failure.compareAndSet(null, t);
            }
        }

        private static void offerSpinning(SpscArrayQueue<DepthLatencyEnvelope> queue, DepthLatencyEnvelope envelope) {
            while (!queue.offer(envelope)) {
                Thread.onSpinWait();
            }
        }

        private static DepthLatencyEnvelope pollSpinning(SpscArrayQueue<DepthLatencyEnvelope> queue) {
            DepthLatencyEnvelope envelope;
            while ((envelope = queue.poll()) == null) {
                Thread.onSpinWait();
            }
            return envelope;
        }
    }
}

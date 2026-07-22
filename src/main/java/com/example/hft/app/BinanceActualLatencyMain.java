package com.example.hft.app;

import com.example.hft.exchange.binance.BinanceBookTickerSource;
import com.example.hft.exchange.binance.BinanceTickerParser;
import com.example.hft.marketdata.model.ActualLatencyEnvelope;
import com.example.hft.marketdata.model.ActualMarketDataRecord;
import com.example.hft.marketdata.model.TradingSignal;
import com.example.hft.pipeline.ActualLatencyStats;
import com.example.hft.strategy.MarketDataProcessor;
import com.example.hft.strategy.QuoteValidator;
import com.example.hft.strategy.TradingDecisionEngine;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedWriter;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.Locale;
import org.jctools.queues.SpmcArrayQueue;
import org.jctools.queues.SpscArrayQueue;





public final class BinanceActualLatencyMain {
    private static final List<String> DEFAULT_SYMBOLS = List.of("BTCUSDT", "ETHUSDT", "BNBUSDT");
    private static final int DEFAULT_LIMIT = 50;
    private static final int DEFAULT_WORKERS = 4;
    private static final int QUEUE_CAPACITY = 4_096;
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private BinanceActualLatencyMain() {
    }

    public static void main(String[] args) throws Exception {
        int limit = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_LIMIT;
        List<String> symbols = args.length > 1 ? List.of(args[1].split(",")) : DEFAULT_SYMBOLS;
        String mode = args.length > 2 ? args[2] : "v5-spsc";
        int workers = args.length > 3 ? Integer.parseInt(args[3]) : DEFAULT_WORKERS;
        Path output = args.length > 4 ? Path.of(args[4]) : defaultOutputPath();

        ActualLatencyStats stats = new ActualLatencyStats(mode, limit);
        List<ActualMarketDataRecord> records = new ArrayList<>(limit);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        LiveQueue queue = switch (mode) {
            case "blocking" -> new BlockingLiveQueue(workers, stats, failure);
            case "v5-spmc" -> new SpmcLiveQueue(workers, stats, failure);
            case "v5-spsc" -> new SpscPartitionedLiveQueue(workers, stats, failure);
            default -> throw new IllegalArgumentException("mode must be blocking, v5-spmc, or v5-spsc");
        };

        queue.start();
        try {
            collectFromWebSocket(limit, symbols, queue, stats, records, failure);
        } finally {
            queue.stopAndJoin();
        }

        if (failure.get() != null) {
            throw new IllegalStateException("actual latency pipeline failed", failure.get());
        }

        writeJsonLines(output, records, mode, workers);
        System.out.println("stored actual Binance.US ticker messages=" + records.size() + " file=" + output);
        System.out.println(stats.toDisplayLine());
    }

    private static void collectFromWebSocket(int limit, List<String> symbols, LiveQueue queue, ActualLatencyStats stats,
                                             List<ActualMarketDataRecord> records, AtomicReference<Throwable> failure)
            throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        BinanceTickerParser parser = new BinanceTickerParser();
        URI uri = URI.create(BinanceBookTickerSource.BINANCE_US_STREAM_URI + streamNames(symbols));

        WebSocket webSocket = HttpClient.newHttpClient()
                .newWebSocketBuilder()
                .buildAsync(uri, new WebSocket.Listener() {
                    private final StringBuilder buffer = new StringBuilder();
                    private long sequenceNumber;
                    private int received;

                    @Override
                    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                        long localReceivedEpochMillis = System.currentTimeMillis();
                        long rawReceivedNanos = System.nanoTime();
                        buffer.append(data);
                        if (!last) {
                            webSocket.request(1);
                            return null;
                        }

                        try {
                            ActualMarketDataRecord record = parser.parse(buffer.toString(), ++sequenceNumber,
                                    localReceivedEpochMillis, rawReceivedNanos);
                            long enqueueStart = System.nanoTime();
                            queue.publish(record, enqueueStart);
                            stats.addProducerOfferNanos(System.nanoTime() - enqueueStart);
                            records.add(record);
                            received++;

                            if (received >= limit) {
                                done.countDown();
                                webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "collected requested ticker events");
                            }
                        } catch (Throwable t) {
                            failure.compareAndSet(null, t);
                            done.countDown();
                            webSocket.abort();
                        } finally {
                            buffer.setLength(0);
                            webSocket.request(1);
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
        boolean completed = done.await(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        if (!completed) {
            webSocket.abort();
            throw new IllegalStateException("timed out waiting for live Binance.US ticker data from " + uri);
        }
    }

    private static void consume(ActualLatencyEnvelope envelope, MarketDataProcessor processor, ActualLatencyStats stats) {
        long processingStart = System.nanoTime();
        TradingSignal signal = processor.signalFor(envelope.record().quote());
        long processingEnd = System.nanoTime();
        stats.record(envelope, processingStart, processingEnd, signal);
    }

    private static String streamNames(List<String> symbols) {
        List<String> streams = new ArrayList<>(symbols.size());
        for (String symbol : symbols) {
            streams.add(symbol.toLowerCase(Locale.ROOT) + "@ticker");
        }
        return String.join("/", streams);
    }

    private static Path defaultOutputPath() {
        return Path.of("data", "binance-actual-" + Instant.now().toString().replace(":", "") + ".jsonl");
    }

    private static void writeJsonLines(Path output, List<ActualMarketDataRecord> records, String mode, int workers)
            throws Exception {
        Files.createDirectories(output.getParent());
        ObjectMapper mapper = new ObjectMapper();
        try (BufferedWriter writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
            for (ActualMarketDataRecord record : records) {
                ObjectNode line = mapper.createObjectNode();
                line.put("mode", mode);
                line.put("workers", workers);
                line.put("sequenceNumber", record.quote().sequenceNumber());
                line.put("symbol", record.quote().symbol());
                line.put("exchangeEventTimeMillis", record.exchangeEventTimeMillis());
                line.put("localReceivedEpochMillis", record.localReceivedEpochMillis());
                line.put("bidTicks", record.quote().bidPrice().ticks());
                line.put("bidSize", record.quote().bidSize());
                line.put("askTicks", record.quote().askPrice().ticks());
                line.put("askSize", record.quote().askSize());
                line.put("rawPayload", record.rawPayload());
                writer.write(mapper.writeValueAsString(line));
                writer.newLine();
            }
        }
    }

    private interface LiveQueue {
        void start();

        void publish(ActualMarketDataRecord record, long enqueuedNanos) throws InterruptedException;

        void stopAndJoin() throws InterruptedException;
    }

    private static final class BlockingLiveQueue implements LiveQueue {
        private final BlockingQueue<ActualLatencyEnvelope> queue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
        private final List<Thread> threads = new ArrayList<>();
        private final int workers;
        private final ActualLatencyStats stats;
        private final AtomicReference<Throwable> failure;

        private BlockingLiveQueue(int workers, ActualLatencyStats stats, AtomicReference<Throwable> failure) {
            this.workers = workers;
            this.stats = stats;
            this.failure = failure;
        }

        @Override
        public void start() {
            for (int i = 0; i < workers; i++) {
                Thread worker = new Thread(this::runWorker, "actual-blocking-worker-" + (i + 1));
                threads.add(worker);
                worker.start();
            }
        }

        @Override
        public void publish(ActualMarketDataRecord record, long enqueuedNanos) throws InterruptedException {
            queue.put(ActualLatencyEnvelope.record(record, enqueuedNanos));
        }

        @Override
        public void stopAndJoin() throws InterruptedException {
            for (int i = 0; i < workers; i++) {
                queue.put(ActualLatencyEnvelope.stop());
            }
            for (Thread thread : threads) {
                thread.join();
            }
        }

        private void runWorker() {
            MarketDataProcessor processor = new MarketDataProcessor(new QuoteValidator(), new TradingDecisionEngine());
            try {
                while (true) {
                    ActualLatencyEnvelope envelope = queue.take();
                    if (envelope.isStop()) {
                        return;
                    }
                    consume(envelope, processor, stats);
                }
            } catch (Throwable t) {
                failure.compareAndSet(null, t);
            }
        }
    }

    private static final class SpmcLiveQueue implements LiveQueue {
        private final SpmcArrayQueue<ActualLatencyEnvelope> queue = new SpmcArrayQueue<>(QUEUE_CAPACITY);
        private final List<Thread> threads = new ArrayList<>();
        private final int workers;
        private final ActualLatencyStats stats;
        private final AtomicReference<Throwable> failure;

        private SpmcLiveQueue(int workers, ActualLatencyStats stats, AtomicReference<Throwable> failure) {
            this.workers = workers;
            this.stats = stats;
            this.failure = failure;
        }

        @Override
        public void start() {
            for (int i = 0; i < workers; i++) {
                Thread worker = new Thread(this::runWorker, "actual-spmc-worker-" + (i + 1));
                threads.add(worker);
                worker.start();
            }
        }

        @Override
        public void publish(ActualMarketDataRecord record, long enqueuedNanos) {
            offerSpinning(queue, ActualLatencyEnvelope.record(record, enqueuedNanos));
        }

        @Override
        public void stopAndJoin() throws InterruptedException {
            for (int i = 0; i < workers; i++) {
                offerSpinning(queue, ActualLatencyEnvelope.stop());
            }
            for (Thread thread : threads) {
                thread.join();
            }
        }

        private void runWorker() {
            MarketDataProcessor processor = new MarketDataProcessor(new QuoteValidator(), new TradingDecisionEngine());
            try {
                while (true) {
                    ActualLatencyEnvelope envelope = pollSpinning(queue);
                    if (envelope.isStop()) {
                        return;
                    }
                    consume(envelope, processor, stats);
                }
            } catch (Throwable t) {
                failure.compareAndSet(null, t);
            }
        }
    }

    private static final class SpscPartitionedLiveQueue implements LiveQueue {
        private final SpscArrayQueue<ActualLatencyEnvelope>[] queues;
        private final List<Thread> threads = new ArrayList<>();
        private final ActualLatencyStats stats;
        private final AtomicReference<Throwable> failure;

        @SuppressWarnings("unchecked")
        private SpscPartitionedLiveQueue(int workers, ActualLatencyStats stats, AtomicReference<Throwable> failure) {
            this.queues = new SpscArrayQueue[workers];
            this.stats = stats;
            this.failure = failure;
            for (int i = 0; i < workers; i++) {
                queues[i] = new SpscArrayQueue<>(QUEUE_CAPACITY);
            }
        }

        @Override
        public void start() {
            for (int i = 0; i < queues.length; i++) {
                int workerIndex = i;
                Thread worker = new Thread(() -> runWorker(queues[workerIndex]), "actual-spsc-worker-" + (workerIndex + 1));
                threads.add(worker);
                worker.start();
            }
        }

        @Override
        public void publish(ActualMarketDataRecord record, long enqueuedNanos) {
            int partition = Math.floorMod(record.quote().symbol().hashCode(), queues.length);
            offerSpinning(queues[partition], ActualLatencyEnvelope.record(record, enqueuedNanos));
        }

        @Override
        public void stopAndJoin() throws InterruptedException {
            for (SpscArrayQueue<ActualLatencyEnvelope> queue : queues) {
                offerSpinning(queue, ActualLatencyEnvelope.stop());
            }
            for (Thread thread : threads) {
                thread.join();
            }
        }

        private void runWorker(SpscArrayQueue<ActualLatencyEnvelope> queue) {
            MarketDataProcessor processor = new MarketDataProcessor(new QuoteValidator(), new TradingDecisionEngine());
            try {
                while (true) {
                    ActualLatencyEnvelope envelope = pollSpinning(queue);
                    if (envelope.isStop()) {
                        return;
                    }
                    consume(envelope, processor, stats);
                }
            } catch (Throwable t) {
                failure.compareAndSet(null, t);
            }
        }
    }

    private static void offerSpinning(SpmcArrayQueue<ActualLatencyEnvelope> queue, ActualLatencyEnvelope envelope) {
        while (!queue.offer(envelope)) {
            Thread.onSpinWait();
        }
    }

    private static ActualLatencyEnvelope pollSpinning(SpmcArrayQueue<ActualLatencyEnvelope> queue) {
        ActualLatencyEnvelope envelope;
        while ((envelope = queue.poll()) == null) {
            Thread.onSpinWait();
        }
        return envelope;
    }

    private static void offerSpinning(SpscArrayQueue<ActualLatencyEnvelope> queue, ActualLatencyEnvelope envelope) {
        while (!queue.offer(envelope)) {
            Thread.onSpinWait();
        }
    }

    private static ActualLatencyEnvelope pollSpinning(SpscArrayQueue<ActualLatencyEnvelope> queue) {
        ActualLatencyEnvelope envelope;
        while ((envelope = queue.poll()) == null) {
            Thread.onSpinWait();
        }
        return envelope;
    }
}

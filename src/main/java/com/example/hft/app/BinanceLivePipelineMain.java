package com.example.hft.app;

import com.example.hft.exchange.binance.BinanceBookTickerParser;
import com.example.hft.exchange.binance.BinanceBookTickerSource;
import com.example.hft.marketdata.model.LiveQuoteEnvelope;
import com.example.hft.marketdata.model.Quote;
import com.example.hft.marketdata.model.TradingSignal;
import com.example.hft.pipeline.LiveLatencyStats;
import com.example.hft.strategy.MarketDataProcessor;
import com.example.hft.strategy.QuoteValidator;
import com.example.hft.strategy.TradingDecisionEngine;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.Locale;



public final class BinanceLivePipelineMain {
    private static final List<String> DEFAULT_SYMBOLS = List.of("BTCUSDT", "ETHUSDT", "BNBUSDT");
    private static final int DEFAULT_LIMIT = 50;
    private static final int QUEUE_CAPACITY = 4_096;
    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    private BinanceLivePipelineMain() {
    }

    public static void main(String[] args) throws Exception {
        int limit = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_LIMIT;
        List<String> symbols = args.length > 1 ? List.of(args[1].split(",")) : DEFAULT_SYMBOLS;

        BlockingQueue<LiveQuoteEnvelope> queue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
        LiveLatencyStats stats = new LiveLatencyStats(limit);
        CountDownLatch workerDone = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        MarketDataProcessor processor = new MarketDataProcessor(new QuoteValidator(), new TradingDecisionEngine());

        Thread worker = new Thread(() -> consume(queue, processor, stats, workerDone, failure), "binance-live-worker");
        worker.start();

        collectFromWebSocket(limit, symbols, queue, stats, failure);
        workerDone.await(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);

        if (failure.get() != null) {
            throw new IllegalStateException("live pipeline failed", failure.get());
        }

        System.out.println("loaded live Binance.US bookTicker messages=" + stats.processed() + " symbols=" + symbols);
        System.out.println("networkLatency=not-measured bookTicker has no exchange event timestamp");
        System.out.println(stats.toDisplayLine());
    }

    private static void consume(BlockingQueue<LiveQuoteEnvelope> queue, MarketDataProcessor processor, LiveLatencyStats stats,
                                CountDownLatch workerDone, AtomicReference<Throwable> failure) {
        try {
            while (true) {
                LiveQuoteEnvelope envelope = queue.take();
                if (envelope.isStop()) {
                    return;
                }
                long processingStart = System.nanoTime();
                TradingSignal signal = processor.signalFor(envelope.quote());
                long processingEnd = System.nanoTime();
                stats.record(envelope, processingStart, processingEnd, signal);
            }
        } catch (Throwable t) {
            failure.compareAndSet(null, t);
        } finally {
            workerDone.countDown();
        }
    }

    private static void collectFromWebSocket(int limit, List<String> symbols, BlockingQueue<LiveQuoteEnvelope> queue,
                                             LiveLatencyStats stats, AtomicReference<Throwable> failure) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        BinanceBookTickerParser parser = new BinanceBookTickerParser();
        URI uri = URI.create(BinanceBookTickerSource.BINANCE_US_STREAM_URI + streamNames(symbols));

        WebSocket webSocket = HttpClient.newHttpClient()
                .newWebSocketBuilder()
                .buildAsync(uri, new WebSocket.Listener() {
                    private final StringBuilder buffer = new StringBuilder();
                    private long sequenceNumber;
                    private int received;

                    @Override
                    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                        long rawReceivedNanos = System.nanoTime();
                        buffer.append(data);
                        if (!last) {
                            webSocket.request(1);
                            return null;
                        }

                        try {
                            Quote quote = parser.parseQuote(buffer.toString(), ++sequenceNumber, rawReceivedNanos);
                            long parsedNanos = System.nanoTime();
                            long enqueueStart = System.nanoTime();
                            queue.put(LiveQuoteEnvelope.quote(quote, rawReceivedNanos, parsedNanos, enqueueStart));
                            stats.addProducerOfferNanos(System.nanoTime() - enqueueStart);
                            received++;

                            if (received >= limit) {
                                queue.put(LiveQuoteEnvelope.stop());
                                done.countDown();
                                webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "collected requested quotes");
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
            queue.offer(LiveQuoteEnvelope.stop());
            throw new IllegalStateException("timed out waiting for live Binance.US data from " + uri);
        }
        if (failure.get() != null) {
            queue.offer(LiveQuoteEnvelope.stop());
        }
    }

    private static String streamNames(List<String> symbols) {
        List<String> streams = new ArrayList<>(symbols.size());
        for (String symbol : symbols) {
            streams.add(symbol.toLowerCase(Locale.ROOT) + "@bookTicker");
        }
        return String.join("/", streams);
    }
}

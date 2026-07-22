package com.example.hft.exchange.binance;

import com.example.hft.marketdata.model.Quote;
import com.example.hft.marketdata.source.QuoteSource;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.Locale;



public final class BinanceBookTickerSource implements QuoteSource {
    public static final URI BINANCE_GLOBAL_STREAM_URI = URI.create("wss://stream.binance.com:9443/stream?streams=");
    public static final URI BINANCE_US_STREAM_URI = URI.create("wss://stream.binance.us:9443/stream?streams=");

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);

    private final URI baseStreamUri;
    private final List<String> symbols;
    private final Duration timeout;
    private final BinanceBookTickerParser parser = new BinanceBookTickerParser();

    public BinanceBookTickerSource(List<String> symbols) {
        this(BINANCE_US_STREAM_URI, symbols, DEFAULT_TIMEOUT);
    }

    public BinanceBookTickerSource(URI baseStreamUri, List<String> symbols, Duration timeout) {
        if (symbols.isEmpty()) {
            throw new IllegalArgumentException("symbols must not be empty");
        }
        this.baseStreamUri = baseStreamUri;
        this.symbols = List.copyOf(symbols);
        this.timeout = timeout;
    }

    @Override
    public List<Quote> load(int limit) throws Exception {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }

        List<Quote> quotes = new ArrayList<>(limit);
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        URI uri = URI.create(baseStreamUri + streamNames());

        WebSocket webSocket = HttpClient.newHttpClient()
                .newWebSocketBuilder()
                .buildAsync(uri, new WebSocket.Listener() {
                    private final StringBuilder buffer = new StringBuilder();
                    private long sequenceNumber;

                    @Override
                    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                        long receivedNanos = System.nanoTime();
                        buffer.append(data);
                        if (!last) {
                            webSocket.request(1);
                            return null;
                        }

                        try {
                            Quote quote = parser.parseQuote(buffer.toString(), ++sequenceNumber, receivedNanos);
                            quotes.add(quote);
                            if (quotes.size() >= limit) {
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
        boolean completed = done.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
        if (!completed) {
            webSocket.abort();
            throw new IllegalStateException("timed out waiting for Binance bookTicker data from " + uri);
        }
        if (failure.get() != null) {
            throw new IllegalStateException("failed to read Binance bookTicker stream from " + uri, failure.get());
        }
        return List.copyOf(quotes);
    }

    private String streamNames() {
        List<String> streams = new ArrayList<>(symbols.size());
        for (String symbol : symbols) {
            streams.add(symbol.toLowerCase(Locale.ROOT) + "@bookTicker");
        }
        return String.join("/", streams);
    }
}

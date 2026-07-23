package com.example.hft.app;

import com.example.hft.datasource.DataSourceModuleVersion;
import com.example.hft.datasource.FanoutMarketDataSink;
import com.example.hft.datasource.MarketDataConnector;
import com.example.hft.datasource.MarketDataSubscription;
import com.example.hft.datasource.TopOfBookMarketDataConnector;
import com.example.hft.datasource.engine.MarketDataCache;
import com.example.hft.datasource.engine.MarketDataEngine;
import com.example.hft.datasource.engine.MarketDataEventBus;
import com.example.hft.datasource.engine.MarketDataListener;
import com.example.hft.datasource.instrument.Instrument;
import com.example.hft.datasource.instrument.SymbolMapper;
import com.example.hft.datasource.normalizer.NormalizedMarketDataEvent;
import com.example.hft.datasource.normalizer.TopOfBookEvent;
import com.example.hft.datasource.replay.RecordingMarketDataSink;
import com.example.hft.exchange.CustomTopOfBookAdapter;
import com.example.hft.exchange.CustomWebSocketTopOfBookAdapter;
import com.example.hft.exchange.TopOfBookComparison;
import com.example.hft.exchange.XChangeTopOfBookClient;
import com.example.hft.exchange.binance.BinanceUsBookTickerWebSocketAdapter;
import com.example.hft.exchange.binance.BinanceUsTopOfBookAdapter;
import com.example.hft.exchange.kraken.KrakenBookWebSocketAdapter;
import com.example.hft.exchange.kraken.KrakenTopOfBookAdapter;
import com.example.hft.exchange.okx.OkxBooks5WebSocketAdapter;
import com.example.hft.exchange.okx.OkxTopOfBookAdapter;
import com.example.hft.marketdata.model.TopOfBookSnapshot;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.MathContext;
import java.net.http.HttpClient;
import java.time.Instant;
import java.util.List;
import java.util.Optional;


public final class CustomWebSocketVsBaselineTopOfBookMain {
    private static final MathContext MATH = MathContext.DECIMAL64;

    private CustomWebSocketVsBaselineTopOfBookMain() {
    }

    public static void main(String[] args) {
        HttpClient httpClient = HttpClient.newHttpClient();
        ObjectMapper objectMapper = new ObjectMapper();
        List<MarketDataConnector> connectors = connectors(httpClient, objectMapper);
        SymbolMapper symbolMapper = new SymbolMapper(instruments());

        MarketDataCache cache = new MarketDataCache();
        MarketDataEventBus eventBus = new MarketDataEventBus();
        CountingMarketDataListener listener = new CountingMarketDataListener();
        eventBus.subscribe(listener);
        MarketDataEngine engine = new MarketDataEngine(cache, eventBus);
        RecordingMarketDataSink recorder = new RecordingMarketDataSink();
        FanoutMarketDataSink sink = FanoutMarketDataSink.of(engine, recorder);
        XChangeTopOfBookClient xchange = new XChangeTopOfBookClient();
        ValidationStats stats = new ValidationStats(connectors.size());

        System.out.println("datasource-engine-websocket-vs-baseline version=" + DataSourceModuleVersion.VERSION
                + " sampledAt=" + Instant.now());
        for (MarketDataConnector connector : connectors) {
            compareOne(connector, xchange, engine, sink, symbolMapper, stats);
        }
        System.out.println("DATASOURCE_ENGINE_SUMMARY version=" + DataSourceModuleVersion.VERSION
                + " cacheTopOfBook=" + cache.topOfBookCount()
                + " publishedEvents=" + listener.events()
                + " replayRecords=" + recorder.records().size()
                + " eventBusListeners=" + eventBus.listenerCount());
        stats.print(cache.topOfBookCount(), listener.events(), recorder.records().size(), eventBus.listenerCount());
    }

    private static List<MarketDataConnector> connectors(HttpClient httpClient, ObjectMapper objectMapper) {
        return List.of(
                connector(
                        new BinanceUsBookTickerWebSocketAdapter(httpClient, objectMapper, "BTCUSDT"),
                        new BinanceUsTopOfBookAdapter(httpClient, objectMapper, "BTCUSDT")
                ),
                connector(
                        new BinanceUsBookTickerWebSocketAdapter(httpClient, objectMapper, "ETHUSDT"),
                        new BinanceUsTopOfBookAdapter(httpClient, objectMapper, "ETHUSDT")
                ),
                connector(
                        new OkxBooks5WebSocketAdapter(httpClient, objectMapper, "BTC-USDT"),
                        new OkxTopOfBookAdapter(httpClient, objectMapper, "BTC-USDT")
                ),
                connector(
                        new OkxBooks5WebSocketAdapter(httpClient, objectMapper, "ETH-USDT"),
                        new OkxTopOfBookAdapter(httpClient, objectMapper, "ETH-USDT")
                ),
                connector(
                        new KrakenBookWebSocketAdapter(httpClient, objectMapper, "BTC/USD"),
                        new KrakenTopOfBookAdapter(httpClient, objectMapper, "BTC/USD", "XBTUSD")
                ),
                connector(
                        new KrakenBookWebSocketAdapter(httpClient, objectMapper, "ETH/USD"),
                        new KrakenTopOfBookAdapter(httpClient, objectMapper, "ETH/USD", "ETHUSD")
                )
        );
    }

    private static List<Instrument> instruments() {
        return List.of(
                instrument("BINANCE_US", "BTCUSDT", "BTC/USD", "BTC", "USD", "0.01", "0.00001"),
                instrument("BINANCE_US", "ETHUSDT", "ETH/USD", "ETH", "USD", "0.01", "0.0001"),
                instrument("OKX", "BTC-USDT", "BTC/USD", "BTC", "USD", "0.1", "0.00000001"),
                instrument("OKX", "ETH-USDT", "ETH/USD", "ETH", "USD", "0.01", "0.000001"),
                instrument("KRAKEN", "BTC/USD", "BTC/USD", "BTC", "USD", "0.1", "0.00000001"),
                instrument("KRAKEN", "ETH/USD", "ETH/USD", "ETH", "USD", "0.01", "0.00000001")
        );
    }

    private static Instrument instrument(String exchange, String exchangeSymbol, String canonicalSymbol,
                                         String baseAsset, String quoteAsset, String tickSize, String lotSize) {
        return new Instrument(exchange, exchangeSymbol, canonicalSymbol, baseAsset, quoteAsset,
                new BigDecimal(tickSize), new BigDecimal(lotSize));
    }

    private static MarketDataConnector connector(CustomWebSocketTopOfBookAdapter websocketAdapter,
                                                 CustomTopOfBookAdapter snapshotAdapter) {
        return new TopOfBookMarketDataConnector(websocketAdapter, snapshotAdapter);
    }

    private static void compareOne(MarketDataConnector connector, XChangeTopOfBookClient xchange,
                                   MarketDataEngine engine, FanoutMarketDataSink sink, SymbolMapper symbolMapper,
                                   ValidationStats stats) {
        try {
            MarketDataSubscription subscription = MarketDataSubscription.topOfBook(connector.exchange(), connector.symbol());
            long subscribeStartNanos = System.nanoTime();
            connector.subscribe(subscription, sink);
            long subscribeDoneNanos = System.nanoTime();
            TopOfBookEvent event = engine.cache().topOfBook(connector.exchange(), connector.symbol())
                    .orElseThrow(() -> new IllegalStateException("no cached top-of-book for " + connector.name()));
            TopOfBookSnapshot websocket = event.toSnapshot();
            long engineEtlNanos = Math.max(0L, subscribeDoneNanos - subscribeStartNanos - websocket.elapsedNanos());
            stats.recordWebSocket(websocket.elapsedNanos(), engineEtlNanos);

            String canonicalSymbol = symbolMapper.byExchangeSymbol(connector.exchange(), connector.symbol())
                    .map(Instrument::canonicalSymbol)
                    .orElse(connector.symbol());
            System.out.println(websocket.display()
                    + " canonical=" + canonicalSymbol
                    + " connectorStatus=" + connector.status()
                    + " engineEtlUs=" + formatMicros(engineEtlNanos));

            try {
                Optional<TopOfBookSnapshot> snapshot = connector.fetchSnapshot(connector.symbol());
                if (snapshot.isPresent()) {
                    ComparisonMetrics metrics = ComparisonMetrics.between(websocket, snapshot.get());
                    stats.recordRest(metrics);
                    System.out.println(snapshot.get().display());
                    System.out.println("WS_VS_REST " + TopOfBookComparison.compare(websocket, snapshot.get()));
                } else {
                    System.out.println("CUSTOM_REST_SKIP exchange=" + connector.exchange()
                            + " symbol=" + connector.symbol()
                            + " reason=no REST snapshot adapter configured");
                }
            } catch (Exception t) {
                stats.recordRestFailure();
                System.out.println("CUSTOM_REST_FAIL exchange=" + connector.exchange()
                        + " symbol=" + connector.symbol()
                        + " error=" + t.getClass().getSimpleName()
                        + ": " + clean(t.getMessage()));
            }

            if (!hasXChangeBaseline(connector.exchange())) {
                System.out.println("XCHANGE_SKIP exchange=" + connector.exchange()
                        + " symbol=" + connector.symbol()
                        + " reason=no XChange baseline configured");
                return;
            }

            try {
                TopOfBookSnapshot baseline = xchange.fetch(connector.exchange(), connector.symbol());
                ComparisonMetrics metrics = ComparisonMetrics.between(websocket, baseline);
                stats.recordXChange(metrics);
                System.out.println(baseline.display());
                System.out.println("WS_VS_XCHANGE " + TopOfBookComparison.compare(websocket, baseline));
            } catch (Exception t) {
                stats.recordXChangeFailure();
                System.out.println("XCHANGE_FAIL exchange=" + connector.exchange()
                        + " symbol=" + connector.symbol()
                        + " error=" + t.getClass().getSimpleName()
                        + ": " + clean(t.getMessage()));
            }
        } catch (Exception t) {
            stats.recordWebSocketFailure();
            System.out.println("CUSTOM_WS_FAIL exchange=" + connector.exchange()
                    + " symbol=" + connector.symbol()
                    + " error=" + t.getClass().getSimpleName()
                    + ": " + clean(t.getMessage()));
        }
    }

    private static boolean hasXChangeBaseline(String exchange) {
        return "BINANCE_US".equals(exchange) || "KRAKEN".equals(exchange);
    }

    private static String clean(String message) {
        if (message == null) {
            return "no message";
        }
        return message.replace('\n', ' ').replace('\r', ' ');
    }

    private static BigDecimal basisPoints(BigDecimal diff, BigDecimal price) {
        if (price.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return diff.divide(price, MATH).multiply(BigDecimal.valueOf(10_000), MATH);
    }

    private static String formatBps(double value) {
        return String.format("%.6f", value);
    }

    private static String formatMillis(double value) {
        return String.format("%.2f", value);
    }

    private static String formatMicros(double value) {
        return String.format("%.2f", value);
    }

    private static String formatMicros(long nanos) {
        return formatMicros(nanos / 1_000.0);
    }

    private record ComparisonMetrics(BigDecimal bidDiff, BigDecimal askDiff, BigDecimal bidDiffBps,
                                     BigDecimal askDiffBps) {
        private static ComparisonMetrics between(TopOfBookSnapshot left, TopOfBookSnapshot right) {
            BigDecimal bidDiff = left.bidPrice().subtract(right.bidPrice()).abs();
            BigDecimal askDiff = left.askPrice().subtract(right.askPrice()).abs();
            return new ComparisonMetrics(bidDiff, askDiff, basisPoints(bidDiff, left.bidPrice()),
                    basisPoints(askDiff, left.askPrice()));
        }

        private boolean exactPriceMatch() {
            return bidDiff.signum() == 0 && askDiff.signum() == 0;
        }
    }

    private static final class ValidationStats {
        private final int connectors;
        private int wsSuccess;
        private int wsFailure;
        private int restSuccess;
        private int restFailure;
        private int restExact;
        private int xchangeSuccess;
        private int xchangeFailure;
        private int xchangeExact;
        private long wsLoadNanos;
        private long engineEtlNanos;
        private double restBidBps;
        private double restAskBps;
        private double xchangeBidBps;
        private double xchangeAskBps;

        private ValidationStats(int connectors) {
            this.connectors = connectors;
        }

        private void recordWebSocket(long loadNanos, long etlNanos) {
            wsSuccess++;
            wsLoadNanos += loadNanos;
            engineEtlNanos += etlNanos;
        }

        private void recordWebSocketFailure() {
            wsFailure++;
        }

        private void recordRest(ComparisonMetrics metrics) {
            restSuccess++;
            if (metrics.exactPriceMatch()) {
                restExact++;
            }
            restBidBps += metrics.bidDiffBps().doubleValue();
            restAskBps += metrics.askDiffBps().doubleValue();
        }

        private void recordRestFailure() {
            restFailure++;
        }

        private void recordXChange(ComparisonMetrics metrics) {
            xchangeSuccess++;
            if (metrics.exactPriceMatch()) {
                xchangeExact++;
            }
            xchangeBidBps += metrics.bidDiffBps().doubleValue();
            xchangeAskBps += metrics.askDiffBps().doubleValue();
        }

        private void recordXChangeFailure() {
            xchangeFailure++;
        }

        private void print(int cacheTopOfBook, int publishedEvents, int replayRecords, int eventBusListeners) {
            double avgWsLoadMs = wsSuccess == 0 ? 0.0 : wsLoadNanos / 1_000_000.0 / wsSuccess;
            double avgEngineEtlUs = wsSuccess == 0 ? 0.0 : engineEtlNanos / 1_000.0 / wsSuccess;
            double avgRestBidBps = restSuccess == 0 ? 0.0 : restBidBps / restSuccess;
            double avgRestAskBps = restSuccess == 0 ? 0.0 : restAskBps / restSuccess;
            double avgXChangeBidBps = xchangeSuccess == 0 ? 0.0 : xchangeBidBps / xchangeSuccess;
            double avgXChangeAskBps = xchangeSuccess == 0 ? 0.0 : xchangeAskBps / xchangeSuccess;
            System.out.println("DATASOURCE_VALIDATION_SUMMARY version=" + DataSourceModuleVersion.VERSION
                    + " connectors=" + connectors
                    + " wsSuccess=" + wsSuccess
                    + " wsFailure=" + wsFailure
                    + " restSuccess=" + restSuccess
                    + " restFailure=" + restFailure
                    + " restExact=" + restExact
                    + " xchangeSuccess=" + xchangeSuccess
                    + " xchangeFailure=" + xchangeFailure
                    + " xchangeExact=" + xchangeExact
                    + " avgWsLoadMs=" + formatMillis(avgWsLoadMs)
                    + " avgEngineEtlUs=" + formatMicros(avgEngineEtlUs)
                    + " avgRestBidDiffBps=" + formatBps(avgRestBidBps)
                    + " avgRestAskDiffBps=" + formatBps(avgRestAskBps)
                    + " avgXChangeBidDiffBps=" + formatBps(avgXChangeBidBps)
                    + " avgXChangeAskDiffBps=" + formatBps(avgXChangeAskBps)
                    + " cacheTopOfBook=" + cacheTopOfBook
                    + " publishedEvents=" + publishedEvents
                    + " replayRecords=" + replayRecords
                    + " eventBusListeners=" + eventBusListeners);
        }
    }

    private static final class CountingMarketDataListener implements MarketDataListener {
        private int events;

        @Override
        public void onMarketData(NormalizedMarketDataEvent event) {
            events++;
        }

        private int events() {
            return events;
        }
    }
}
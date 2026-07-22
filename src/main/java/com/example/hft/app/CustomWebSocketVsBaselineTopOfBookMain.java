package com.example.hft.app;

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
import java.net.http.HttpClient;
import java.time.Instant;
import java.util.List;
import java.util.Optional;


public final class CustomWebSocketVsBaselineTopOfBookMain {
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

        System.out.println("datasource-engine-websocket-vs-baseline sampledAt=" + Instant.now());
        for (MarketDataConnector connector : connectors) {
            compareOne(connector, xchange, engine, sink, symbolMapper);
        }
        System.out.println("DATASOURCE_ENGINE_SUMMARY cacheTopOfBook=" + cache.topOfBookCount()
                + " publishedEvents=" + listener.events()
                + " replayRecords=" + recorder.records().size()
                + " eventBusListeners=" + eventBus.listenerCount());
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
                                   MarketDataEngine engine, FanoutMarketDataSink sink, SymbolMapper symbolMapper) {
        try {
            MarketDataSubscription subscription = MarketDataSubscription.topOfBook(connector.exchange(), connector.symbol());
            connector.subscribe(subscription, sink);
            TopOfBookEvent event = engine.cache().topOfBook(connector.exchange(), connector.symbol())
                    .orElseThrow(() -> new IllegalStateException("no cached top-of-book for " + connector.name()));
            TopOfBookSnapshot websocket = event.toSnapshot();
            String canonicalSymbol = symbolMapper.byExchangeSymbol(connector.exchange(), connector.symbol())
                    .map(Instrument::canonicalSymbol)
                    .orElse(connector.symbol());
            System.out.println(websocket.display()
                    + " canonical=" + canonicalSymbol
                    + " connectorStatus=" + connector.status());

            try {
                Optional<TopOfBookSnapshot> snapshot = connector.fetchSnapshot(connector.symbol());
                if (snapshot.isPresent()) {
                    System.out.println(snapshot.get().display());
                    System.out.println("WS_VS_REST " + TopOfBookComparison.compare(websocket, snapshot.get()));
                } else {
                    System.out.println("CUSTOM_REST_SKIP exchange=" + connector.exchange()
                            + " symbol=" + connector.symbol()
                            + " reason=no REST snapshot adapter configured");
                }
            } catch (Exception t) {
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
                System.out.println(baseline.display());
                System.out.println("WS_VS_XCHANGE " + TopOfBookComparison.compare(websocket, baseline));
            } catch (Exception t) {
                System.out.println("XCHANGE_FAIL exchange=" + connector.exchange()
                        + " symbol=" + connector.symbol()
                        + " error=" + t.getClass().getSimpleName()
                        + ": " + clean(t.getMessage()));
            }
        } catch (Exception t) {
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
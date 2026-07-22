package com.example.hft.app;

import com.example.hft.datasource.MarketDataConnector;
import com.example.hft.datasource.TopOfBookMarketDataConnector;
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
        List<MarketDataConnector> connectors = List.of(
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
        XChangeTopOfBookClient xchange = new XChangeTopOfBookClient();

        System.out.println("datasource-websocket-vs-baseline sampledAt=" + Instant.now());
        for (MarketDataConnector connector : connectors) {
            compareOne(connector, xchange);
        }
    }

    private static MarketDataConnector connector(CustomWebSocketTopOfBookAdapter websocketAdapter,
                                                 CustomTopOfBookAdapter snapshotAdapter) {
        return new TopOfBookMarketDataConnector(websocketAdapter, snapshotAdapter);
    }

    private static void compareOne(MarketDataConnector connector, XChangeTopOfBookClient xchange) {
        try {
            TopOfBookSnapshot websocket = connector.fetchTopOfBook(connector.symbol());
            System.out.println(websocket.display());

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
}
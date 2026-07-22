package com.example.hft.app;

import com.example.hft.exchange.binance.BinanceUsBookTickerWebSocketAdapter;
import com.example.hft.exchange.binance.BinanceUsTopOfBookAdapter;
import com.example.hft.exchange.CustomTopOfBookAdapter;
import com.example.hft.exchange.CustomWebSocketTopOfBookAdapter;
import com.example.hft.exchange.kraken.KrakenBookWebSocketAdapter;
import com.example.hft.exchange.kraken.KrakenTopOfBookAdapter;
import com.example.hft.exchange.okx.OkxBooks5WebSocketAdapter;
import com.example.hft.exchange.okx.OkxTopOfBookAdapter;
import com.example.hft.exchange.TopOfBookComparison;
import com.example.hft.exchange.XChangeTopOfBookClient;
import com.example.hft.marketdata.model.TopOfBookSnapshot;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.time.Instant;
import java.util.List;




public final class CustomWebSocketVsBaselineTopOfBookMain {
    private CustomWebSocketVsBaselineTopOfBookMain() {
    }

    public static void main(String[] args) {
        HttpClient httpClient = HttpClient.newHttpClient();
        ObjectMapper objectMapper = new ObjectMapper();
        List<CustomWebSocketTopOfBookAdapter> websocketAdapters = List.of(
                new BinanceUsBookTickerWebSocketAdapter(httpClient, objectMapper, "BTCUSDT"),
                new BinanceUsBookTickerWebSocketAdapter(httpClient, objectMapper, "ETHUSDT"),
                new OkxBooks5WebSocketAdapter(httpClient, objectMapper, "BTC-USDT"),
                new OkxBooks5WebSocketAdapter(httpClient, objectMapper, "ETH-USDT"),
                new KrakenBookWebSocketAdapter(httpClient, objectMapper, "BTC/USD"),
                new KrakenBookWebSocketAdapter(httpClient, objectMapper, "ETH/USD")
        );
        List<CustomTopOfBookAdapter> restAdapters = List.of(
                new BinanceUsTopOfBookAdapter(httpClient, objectMapper, "BTCUSDT"),
                new BinanceUsTopOfBookAdapter(httpClient, objectMapper, "ETHUSDT"),
                new OkxTopOfBookAdapter(httpClient, objectMapper, "BTC-USDT"),
                new OkxTopOfBookAdapter(httpClient, objectMapper, "ETH-USDT"),
                new KrakenTopOfBookAdapter(httpClient, objectMapper, "BTC/USD", "XBTUSD"),
                new KrakenTopOfBookAdapter(httpClient, objectMapper, "ETH/USD", "ETHUSD")
        );
        XChangeTopOfBookClient xchange = new XChangeTopOfBookClient();

        System.out.println("custom-websocket-vs-baseline sampledAt=" + Instant.now());
        for (CustomWebSocketTopOfBookAdapter adapter : websocketAdapters) {
            compareOne(adapter, restAdapters, xchange);
        }
    }

    private static void compareOne(CustomWebSocketTopOfBookAdapter websocketAdapter,
                                   List<CustomTopOfBookAdapter> restAdapters,
                                   XChangeTopOfBookClient xchange) {
        try {
            TopOfBookSnapshot websocket = websocketAdapter.fetch();
            System.out.println(websocket.display());

            try {
                TopOfBookSnapshot rest = restAdapter(websocketAdapter, restAdapters).fetch();
                System.out.println(rest.display());
                System.out.println("WS_VS_REST " + TopOfBookComparison.compare(websocket, rest));
            } catch (Throwable t) {
                System.out.println("CUSTOM_REST_FAIL exchange=" + websocketAdapter.exchange()
                        + " symbol=" + websocketAdapter.symbol()
                        + " error=" + t.getClass().getSimpleName()
                        + ": " + clean(t.getMessage()));
            }

            if (!hasXChangeBaseline(websocketAdapter.exchange())) {
                System.out.println("XCHANGE_SKIP exchange=" + websocketAdapter.exchange()
                        + " symbol=" + websocketAdapter.symbol()
                        + " reason=no XChange baseline configured");
                return;
            }

            try {
                TopOfBookSnapshot baseline = xchange.fetch(websocketAdapter.exchange(), websocketAdapter.symbol());
                System.out.println(baseline.display());
                System.out.println("WS_VS_XCHANGE " + TopOfBookComparison.compare(websocket, baseline));
            } catch (Throwable t) {
                System.out.println("XCHANGE_FAIL exchange=" + websocketAdapter.exchange()
                        + " symbol=" + websocketAdapter.symbol()
                        + " error=" + t.getClass().getSimpleName()
                        + ": " + clean(t.getMessage()));
            }
        } catch (Throwable t) {
            System.out.println("CUSTOM_WS_FAIL exchange=" + websocketAdapter.exchange()
                    + " symbol=" + websocketAdapter.symbol()
                    + " error=" + t.getClass().getSimpleName()
                    + ": " + clean(t.getMessage()));
        }
    }

    private static boolean hasXChangeBaseline(String exchange) {
        return "BINANCE_US".equals(exchange) || "KRAKEN".equals(exchange);
    }

    private static CustomTopOfBookAdapter restAdapter(CustomWebSocketTopOfBookAdapter websocketAdapter,
                                                      List<CustomTopOfBookAdapter> restAdapters) {
        for (CustomTopOfBookAdapter restAdapter : restAdapters) {
            if (restAdapter.exchange().equals(websocketAdapter.exchange())
                    && restAdapter.symbol().equals(websocketAdapter.symbol())) {
                return restAdapter;
            }
        }
        throw new IllegalArgumentException("no REST adapter for " + websocketAdapter.exchange() + " " + websocketAdapter.symbol());
    }

    private static String clean(String message) {
        if (message == null) {
            return "no message";
        }
        return message.replace('\n', ' ').replace('\r', ' ');
    }
}

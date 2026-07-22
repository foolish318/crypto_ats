package com.example.hft.app;

import com.example.hft.exchange.binance.BinanceUsTopOfBookAdapter;
import com.example.hft.exchange.CustomTopOfBookAdapter;
import com.example.hft.exchange.kraken.KrakenTopOfBookAdapter;
import com.example.hft.exchange.okx.OkxTopOfBookAdapter;
import com.example.hft.exchange.TopOfBookComparison;
import com.example.hft.exchange.XChangeTopOfBookClient;
import com.example.hft.marketdata.model.TopOfBookSnapshot;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.time.Instant;
import java.util.List;




public final class CustomVsXChangeTopOfBookMain {
    private CustomVsXChangeTopOfBookMain() {
    }

    public static void main(String[] args) {
        HttpClient httpClient = HttpClient.newHttpClient();
        ObjectMapper objectMapper = new ObjectMapper();
        List<CustomTopOfBookAdapter> adapters = List.of(
                new BinanceUsTopOfBookAdapter(httpClient, objectMapper, "BTCUSDT"),
                new BinanceUsTopOfBookAdapter(httpClient, objectMapper, "ETHUSDT"),
                new OkxTopOfBookAdapter(httpClient, objectMapper, "BTC-USDT"),
                new OkxTopOfBookAdapter(httpClient, objectMapper, "ETH-USDT"),
                new KrakenTopOfBookAdapter(httpClient, objectMapper, "BTC/USD", "XBTUSD"),
                new KrakenTopOfBookAdapter(httpClient, objectMapper, "ETH/USD", "ETHUSD")
        );
        XChangeTopOfBookClient xchange = new XChangeTopOfBookClient();

        System.out.println("custom-vs-xchange sampledAt=" + Instant.now());
        for (CustomTopOfBookAdapter adapter : adapters) {
            compareOne(adapter, xchange);
        }
    }

    private static void compareOne(CustomTopOfBookAdapter adapter, XChangeTopOfBookClient xchange) {
        try {
            TopOfBookSnapshot custom = adapter.fetch();
            System.out.println(custom.display());
            if (!hasXChangeBaseline(adapter.exchange())) {
                System.out.println("XCHANGE_SKIP exchange=" + adapter.exchange()
                        + " symbol=" + adapter.symbol()
                        + " reason=no XChange baseline configured");
                return;
            }
            try {
                TopOfBookSnapshot baseline = xchange.fetch(adapter.exchange(), adapter.symbol());
                System.out.println(baseline.display());
                System.out.println(TopOfBookComparison.compare(custom, baseline));
            } catch (Throwable t) {
                System.out.println("XCHANGE_FAIL exchange=" + adapter.exchange()
                        + " symbol=" + adapter.symbol()
                        + " error=" + t.getClass().getSimpleName()
                        + ": " + clean(t.getMessage()));
            }
        } catch (Throwable t) {
            System.out.println("CUSTOM_FAIL exchange=" + adapter.exchange()
                    + " symbol=" + adapter.symbol()
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

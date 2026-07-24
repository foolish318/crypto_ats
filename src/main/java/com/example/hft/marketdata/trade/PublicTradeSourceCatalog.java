package com.example.hft.marketdata.trade;

import com.example.hft.marketdata.model.Venue;
import java.net.URI;
import java.util.List;
import java.util.Locale;

public final class PublicTradeSourceCatalog {
    private PublicTradeSourceCatalog() {
    }

    public static List<PublicTradeSourceDefinition> defaultSources() {
        return List.of(
                binanceUs("BTCUSDT"),
                binanceUs("ETHUSDT"),
                okx("BTC-USDT"),
                okx("ETH-USDT"),
                kraken("BTC/USDT"),
                kraken("ETH/USDT")
        );
    }

    public static PublicTradeSourceDefinition binanceUs(String symbol) {
        return new PublicTradeSourceDefinition(
                "binance-us-trades-" + symbol,
                Venue.BINANCE_US,
                symbol,
                URI.create("wss://stream.binance.us:9443/ws/"
                        + symbol.toLowerCase(Locale.ROOT) + "@trade"),
                ""
        );
    }

    public static PublicTradeSourceDefinition okx(String symbol) {
        return new PublicTradeSourceDefinition(
                "okx-trades-" + symbol,
                Venue.OKX,
                symbol,
                URI.create("wss://ws.okx.com:8443/ws/v5/public"),
                "{\"op\":\"subscribe\",\"args\":[{\"channel\":\"trades\",\"instId\":\""
                        + symbol + "\"}]}"
        );
    }

    public static PublicTradeSourceDefinition kraken(String symbol) {
        return new PublicTradeSourceDefinition(
                "kraken-trades-" + symbol.replace('/', '-'),
                Venue.KRAKEN,
                symbol,
                URI.create("wss://ws.kraken.com/v2"),
                "{\"method\":\"subscribe\",\"params\":{\"channel\":\"trade\",\"symbol\":[\""
                        + symbol + "\"],\"snapshot\":false}}"
        );
    }
}
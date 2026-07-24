package com.example.hft.datasource.deepbook;

import java.net.URI;
import java.util.List;
import java.util.Locale;


public final class DeepBookSourceCatalog {
    private static final int BINANCE_SNAPSHOT_LIMIT = 5_000;

    private DeepBookSourceCatalog() {
    }

    public static List<DeepBookSourceDefinition> defaultSources() {
        return List.of(
                binanceUs("BTCUSDT"),
                binanceUs("ETHUSDT"),
                okx("BTC-USDT"),
                okx("ETH-USDT"),
                kraken("BTC/USDT"),
                kraken("ETH/USDT")
        );
    }

    public static DeepBookSourceDefinition binanceUs(String symbol) {
        String lower = symbol.toLowerCase(Locale.ROOT);
        return new DeepBookSourceDefinition(
                "binance-us-" + symbol,
                "BINANCE_US",
                symbol,
                "depth@100ms + REST depth snapshot",
                BINANCE_SNAPSHOT_LIMIT,
                URI.create("wss://stream.binance.us:9443/ws/" + lower + "@depth@100ms"),
                null,
                URI.create("https://api.binance.us/api/v3/depth?symbol=" + symbol + "&limit=" + BINANCE_SNAPSHOT_LIMIT),
                false,
                "Public REST snapshot up to 5000 levels plus public diff-depth WebSocket for local order book maintenance."
        );
    }

    public static DeepBookSourceDefinition okx(String instrumentId) {
        return new DeepBookSourceDefinition(
                "okx-" + instrumentId,
                "OKX",
                instrumentId,
                "books",
                400,
                URI.create("wss://ws.okx.com:8443/ws/v5/public"),
                "{\"op\":\"subscribe\",\"args\":[{\"channel\":\"books\",\"instId\":\"" + instrumentId + "\"}]}",
                null,
                false,
                "Public 400-level incremental order book channel. Tick-by-tick 400/50-level channels require login and VIP tier."
        );
    }

    public static DeepBookSourceDefinition kraken(String pair) {
        return new DeepBookSourceDefinition(
                "kraken-" + pair.replace('/', '-'),
                "KRAKEN",
                pair,
                "book",
                1_000,
                URI.create("wss://ws.kraken.com/v2"),
                "{\"method\":\"subscribe\",\"params\":{\"channel\":\"book\",\"symbol\":[\"" + pair + "\"],\"depth\":1000,\"snapshot\":true}}",
                null,
                false,
                "Public WebSocket v2 L2 book channel with depth 10/25/100/500/1000."
        );
    }
}
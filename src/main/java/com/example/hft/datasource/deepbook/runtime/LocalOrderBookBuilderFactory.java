package com.example.hft.datasource.deepbook.runtime;

import com.example.hft.datasource.deepbook.DeepBookSourceDefinition;
import java.time.Duration;


public final class LocalOrderBookBuilderFactory {
    private static final Duration DEFAULT_MAX_EVENT_AGE = Duration.ofSeconds(30);

    private LocalOrderBookBuilderFactory() {
    }

    public static LocalOrderBookBuilder create(DeepBookSourceDefinition source) {
        return create(source, DEFAULT_MAX_EVENT_AGE);
    }

    public static LocalOrderBookBuilder create(
            DeepBookSourceDefinition source,
            Duration maxEventAge
    ) {
        return switch (source.exchange()) {
            case "BINANCE_US" -> new BinanceLocalOrderBookBuilder(source, maxEventAge);
            case "OKX" -> new OkxLocalOrderBookBuilder(source, maxEventAge);
            case "KRAKEN" -> new KrakenLocalOrderBookBuilder(source, maxEventAge);
            default -> throw new IllegalArgumentException("unsupported exchange " + source.exchange());
        };
    }
}

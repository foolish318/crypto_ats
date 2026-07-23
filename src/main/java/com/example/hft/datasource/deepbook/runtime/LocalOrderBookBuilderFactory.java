package com.example.hft.datasource.deepbook.runtime;

import com.example.hft.datasource.deepbook.DeepBookSourceDefinition;
import com.example.hft.datasource.instrument.Instrument;
import java.time.Duration;


public final class LocalOrderBookBuilderFactory {
    private static final Duration DEFAULT_MAX_EVENT_AGE = Duration.ofSeconds(30);

    private LocalOrderBookBuilderFactory() {
    }

    public static LocalOrderBookBuilder create(DeepBookSourceDefinition source) {
        return create(source, DEFAULT_MAX_EVENT_AGE, null);
    }

    public static LocalOrderBookBuilder create(
            DeepBookSourceDefinition source,
            Duration maxEventAge
    ) {
        return create(source, maxEventAge, null);
    }

    public static LocalOrderBookBuilder create(
            DeepBookSourceDefinition source,
            Instrument instrument
    ) {
        return create(source, DEFAULT_MAX_EVENT_AGE, instrument);
    }

    public static LocalOrderBookBuilder create(
            DeepBookSourceDefinition source,
            Duration maxEventAge,
            Instrument instrument
    ) {
        return switch (source.exchange()) {
            case "BINANCE_US" -> new BinanceLocalOrderBookBuilder(
                    source, maxEventAge, instrument);
            case "OKX" -> new OkxLocalOrderBookBuilder(source, maxEventAge, instrument);
            case "KRAKEN" -> new KrakenLocalOrderBookBuilder(
                    source, maxEventAge, instrument);
            default -> throw new IllegalArgumentException(
                    "unsupported exchange " + source.exchange());
        };
    }
}
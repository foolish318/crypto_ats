package com.example.hft.marketdata.trade;

import com.example.hft.datasource.instrument.Instrument;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class PublicTradeNormalizerFactory {
    private PublicTradeNormalizerFactory() {
    }

    public static PublicTradeNormalizer create(
            PublicTradeSourceDefinition source,
            Instrument instrument,
            ObjectMapper mapper
    ) {
        return switch (source.venue()) {
            case BINANCE_US -> new BinancePublicTradeNormalizer(source, instrument, mapper);
            case OKX -> new OkxPublicTradeNormalizer(source, instrument, mapper);
            case KRAKEN -> new KrakenPublicTradeNormalizer(source, instrument, mapper);
        };
    }
}
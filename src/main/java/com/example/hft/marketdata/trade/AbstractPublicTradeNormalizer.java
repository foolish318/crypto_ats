package com.example.hft.marketdata.trade;

import com.example.hft.datasource.instrument.Instrument;
import com.example.hft.marketdata.model.AggressorSide;
import com.example.hft.marketdata.model.InstrumentId;
import com.example.hft.marketdata.model.MarketEventHeader;
import com.example.hft.marketdata.model.PublicTrade;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;

abstract class AbstractPublicTradeNormalizer implements PublicTradeNormalizer {
    static final int SCHEMA_VERSION = 1;

    final PublicTradeSourceDefinition source;
    final Instrument instrument;
    final ObjectMapper mapper;

    AbstractPublicTradeNormalizer(
            PublicTradeSourceDefinition source,
            Instrument instrument,
            ObjectMapper mapper
    ) {
        if (source == null || instrument == null || mapper == null) {
            throw new IllegalArgumentException("source, instrument, and mapper are required");
        }
        if (!source.venue().name().equals(instrument.exchange())
                || !source.venueSymbol().equals(instrument.exchangeSymbol())) {
            throw new IllegalArgumentException("trade source does not match instrument metadata");
        }
        this.source = source;
        this.instrument = instrument;
        this.mapper = mapper;
    }

    final PublicTrade trade(
            String tradeId,
            BigDecimal price,
            BigDecimal quantity,
            AggressorSide aggressorSide,
            Long sourceSequence,
            long localSequence,
            long streamEpoch,
            long exchangeEpochMillis,
            long receiveEpochMillis,
            long receiveNanos
    ) {
        MarketEventHeader header = new MarketEventHeader(
                source.venue(),
                new InstrumentId(instrument.canonicalSymbol()),
                source.venueSymbol(),
                sourceSequence,
                localSequence,
                streamEpoch,
                exchangeEpochMillis,
                receiveEpochMillis,
                receiveNanos,
                System.nanoTime(),
                SCHEMA_VERSION
        );
        return new PublicTrade(
                source.id(), header, tradeId, price, quantity, aggressorSide
        );
    }

    static BigDecimal positiveDecimal(JsonNode node, String field) {
        String text = requiredText(node, field);
        BigDecimal value;
        try {
            value = new BigDecimal(text);
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException(field + " is not a decimal", error);
        }
        if (value.signum() <= 0) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return value;
    }

    static long positiveLong(JsonNode node, String field) {
        long value = node.path(field).asLong(Long.MIN_VALUE);
        if (value <= 0L) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return value;
    }

    static String requiredText(JsonNode node, String field) {
        String value = node.path(field).asText("");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }

    static Long numericId(String value) {
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
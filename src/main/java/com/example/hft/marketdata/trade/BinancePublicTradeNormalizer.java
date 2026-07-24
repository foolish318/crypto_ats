package com.example.hft.marketdata.trade;

import com.example.hft.datasource.instrument.Instrument;
import com.example.hft.marketdata.model.AggressorSide;
import com.example.hft.marketdata.model.PublicTrade;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.function.LongSupplier;

public final class BinancePublicTradeNormalizer extends AbstractPublicTradeNormalizer {
    public BinancePublicTradeNormalizer(
            PublicTradeSourceDefinition source,
            Instrument instrument,
            ObjectMapper mapper
    ) {
        super(source, instrument, mapper);
    }

    @Override
    public List<PublicTrade> normalize(
            String payload,
            long streamEpoch,
            long receiveEpochMillis,
            long receiveNanos,
            LongSupplier localSequence
    ) throws Exception {
        JsonNode parsed = mapper.readTree(payload);
        JsonNode root = parsed.has("data") ? parsed.path("data") : parsed;
        if (!"trade".equals(root.path("e").asText())
                || !source.venueSymbol().equals(root.path("s").asText())) {
            return List.of();
        }
        long tradeId = positiveLong(root, "t");
        long exchangeTime = positiveLong(root, "T");
        AggressorSide side = root.path("m").asBoolean(false)
                ? AggressorSide.SELL
                : AggressorSide.BUY;
        return List.of(trade(
                Long.toString(tradeId),
                positiveDecimal(root, "p"),
                positiveDecimal(root, "q"),
                side,
                tradeId,
                localSequence.getAsLong(),
                streamEpoch,
                exchangeTime,
                receiveEpochMillis,
                receiveNanos
        ));
    }
}
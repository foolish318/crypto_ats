package com.example.hft.marketdata.trade;

import com.example.hft.datasource.instrument.Instrument;
import com.example.hft.marketdata.model.AggressorSide;
import com.example.hft.marketdata.model.PublicTrade;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;

public final class OkxPublicTradeNormalizer extends AbstractPublicTradeNormalizer {
    public OkxPublicTradeNormalizer(
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
        JsonNode root = mapper.readTree(payload);
        if (!"trades".equals(root.path("arg").path("channel").asText())
                || !source.venueSymbol().equals(root.path("arg").path("instId").asText())) {
            return List.of();
        }
        JsonNode data = root.path("data");
        if (!data.isArray()) {
            throw new IllegalArgumentException("OKX trade data must be an array");
        }
        List<PublicTrade> trades = new ArrayList<>(data.size());
        for (JsonNode item : data) {
            String id = requiredText(item, "tradeId");
            String sideText = requiredText(item, "side");
            AggressorSide side = switch (sideText) {
                case "buy" -> AggressorSide.BUY;
                case "sell" -> AggressorSide.SELL;
                default -> AggressorSide.UNKNOWN;
            };
            trades.add(trade(
                    id,
                    positiveDecimal(item, "px"),
                    positiveDecimal(item, "sz"),
                    side,
                    numericId(id),
                    localSequence.getAsLong(),
                    streamEpoch,
                    positiveLong(item, "ts"),
                    receiveEpochMillis,
                    receiveNanos
            ));
        }
        return List.copyOf(trades);
    }
}
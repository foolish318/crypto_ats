package com.example.hft.marketdata.trade;

import com.example.hft.datasource.instrument.Instrument;
import com.example.hft.marketdata.model.AggressorSide;
import com.example.hft.marketdata.model.PublicTrade;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;

public final class KrakenPublicTradeNormalizer extends AbstractPublicTradeNormalizer {
    public KrakenPublicTradeNormalizer(
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
        if (!"trade".equals(root.path("channel").asText())) {
            return List.of();
        }
        JsonNode data = root.path("data");
        if (!data.isArray()) {
            throw new IllegalArgumentException("Kraken trade data must be an array");
        }
        List<PublicTrade> trades = new ArrayList<>(data.size());
        for (JsonNode item : data) {
            if (!source.venueSymbol().equals(item.path("symbol").asText())) {
                continue;
            }
            String id = requiredText(item, "trade_id");
            String sideText = requiredText(item, "side");
            AggressorSide side = switch (sideText) {
                case "buy" -> AggressorSide.BUY;
                case "sell" -> AggressorSide.SELL;
                default -> AggressorSide.UNKNOWN;
            };
            long exchangeTime = Instant.parse(requiredText(item, "timestamp")).toEpochMilli();
            trades.add(trade(
                    id,
                    positiveDecimal(item, "price"),
                    positiveDecimal(item, "qty"),
                    side,
                    numericId(id),
                    localSequence.getAsLong(),
                    streamEpoch,
                    exchangeTime,
                    receiveEpochMillis,
                    receiveNanos
            ));
        }
        return List.copyOf(trades);
    }
}
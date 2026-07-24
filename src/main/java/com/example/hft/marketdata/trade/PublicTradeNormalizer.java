package com.example.hft.marketdata.trade;

import com.example.hft.marketdata.model.PublicTrade;
import java.util.List;
import java.util.function.LongSupplier;

public interface PublicTradeNormalizer {
    List<PublicTrade> normalize(
            String payload,
            long streamEpoch,
            long receiveEpochMillis,
            long receiveNanos,
            LongSupplier localSequence
    ) throws Exception;
}
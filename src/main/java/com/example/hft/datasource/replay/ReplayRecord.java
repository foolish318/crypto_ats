package com.example.hft.datasource.replay;

import com.example.hft.datasource.normalizer.NormalizedMarketDataEvent;


public record ReplayRecord(
        long originalReceivedNanos,
        NormalizedMarketDataEvent event
) {
}

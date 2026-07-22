package com.example.hft.datasource;

import com.example.hft.datasource.normalizer.NormalizedMarketDataEvent;
import java.util.List;


public final class FanoutMarketDataSink implements MarketDataSink {
    private final List<MarketDataSink> sinks;

    public FanoutMarketDataSink(List<MarketDataSink> sinks) {
        this.sinks = List.copyOf(sinks);
    }

    public static FanoutMarketDataSink of(MarketDataSink first, MarketDataSink second) {
        return new FanoutMarketDataSink(List.of(first, second));
    }

    @Override
    public void onEvent(NormalizedMarketDataEvent event) {
        for (MarketDataSink sink : sinks) {
            sink.onEvent(event);
        }
    }

    @Override
    public void onHealth(DataSourceHealth health) {
        for (MarketDataSink sink : sinks) {
            sink.onHealth(health);
        }
    }

    @Override
    public void onError(String source, Throwable error) {
        for (MarketDataSink sink : sinks) {
            sink.onError(source, error);
        }
    }
}
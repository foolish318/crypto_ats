package com.example.hft.datasource.replay;

import com.example.hft.datasource.MarketDataSink;
import com.example.hft.datasource.normalizer.NormalizedMarketDataEvent;
import java.util.ArrayList;
import java.util.List;


public final class RecordingMarketDataSink implements MarketDataSink {
    private final List<ReplayRecord> records = new ArrayList<>();

    @Override
    public void onEvent(NormalizedMarketDataEvent event) {
        records.add(new ReplayRecord(event.receivedNanos(), event));
    }

    public List<ReplayRecord> records() {
        return List.copyOf(records);
    }
}

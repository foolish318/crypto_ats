package com.example.hft.datasource.replay;

import com.example.hft.datasource.MarketDataSink;
import java.util.List;


public final class ReplayMarketDataSource {
    private final List<ReplayRecord> records;
    private final ReplayClockMode clockMode;

    public ReplayMarketDataSource(List<ReplayRecord> records, ReplayClockMode clockMode) {
        this.records = List.copyOf(records);
        this.clockMode = clockMode;
    }

    public void replay(MarketDataSink sink) {
        long previousNanos = -1L;
        for (ReplayRecord record : records) {
            if (clockMode == ReplayClockMode.ORIGINAL_SPACING && previousNanos >= 0) {
                sleepNanos(Math.max(0L, record.originalReceivedNanos() - previousNanos));
            }
            sink.onEvent(record.event());
            previousNanos = record.originalReceivedNanos();
        }
    }

    private static void sleepNanos(long nanos) {
        if (nanos <= 0) {
            return;
        }
        try {
            Thread.sleep(nanos / 1_000_000L, (int) (nanos % 1_000_000L));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("replay interrupted", e);
        }
    }
}

package com.example.hft.pipeline;

import com.example.hft.marketdata.model.DepthMarketDataRecord;


public interface DepthPipeline {
    String name();

    DepthLatencyStats stats();

    void start();

    void publish(DepthMarketDataRecord record, long enqueuedNanos) throws InterruptedException;

    void stopAndJoin() throws InterruptedException;
}

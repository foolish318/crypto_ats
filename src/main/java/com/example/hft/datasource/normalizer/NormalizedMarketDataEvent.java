package com.example.hft.datasource.normalizer;

import com.example.hft.datasource.transport.TransportType;


public interface NormalizedMarketDataEvent {
    String source();

    String exchange();

    String symbol();

    TransportType transport();

    long receivedNanos();

    long exchangeTimeMillis();

    long sequence();
}

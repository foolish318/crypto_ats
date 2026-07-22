package com.example.hft.datasource;

import java.time.Instant;


public record DataSourceHealth(
        String source,
        String exchange,
        String symbol,
        DataSourceStatus status,
        String detail,
        Instant observedAt
) {
    public static DataSourceHealth live(String source, String exchange, String symbol, String detail) {
        return new DataSourceHealth(source, exchange, symbol, DataSourceStatus.LIVE, detail, Instant.now());
    }

    public static DataSourceHealth degraded(String source, String exchange, String symbol, String detail) {
        return new DataSourceHealth(source, exchange, symbol, DataSourceStatus.DEGRADED, detail, Instant.now());
    }
}

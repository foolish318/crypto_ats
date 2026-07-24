package com.example.hft.marketdata.trade;

public record TradeProcessingResult(
        int normalized,
        int published,
        int duplicates,
        int outOfOrder,
        int invalid,
        String detail
) {
    public TradeProcessingResult {
        detail = detail == null ? "" : detail;
    }
}
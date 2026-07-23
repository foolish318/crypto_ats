package com.example.hft.datasource.deepbook.runtime;


public record RawEnvelope(
        String version,
        RawRecordType recordType,
        long generation,
        String sourceId,
        String exchange,
        String symbol,
        long receivedEpochMillis,
        long receivedNanos,
        String payload,
        String detail
) {
    public RawEnvelope {
        if (version == null || version.isBlank() || recordType == null
                || sourceId == null || sourceId.isBlank()) {
            throw new IllegalArgumentException("raw envelope requires version, recordType, and sourceId");
        }
        exchange = exchange == null ? "" : exchange;
        symbol = symbol == null ? "" : symbol;
        payload = payload == null ? "" : payload;
        detail = detail == null ? "" : detail;
    }
}

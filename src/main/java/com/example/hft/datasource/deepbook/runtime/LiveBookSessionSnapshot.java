package com.example.hft.datasource.deepbook.runtime;


public record LiveBookSessionSnapshot(
        String sourceId,
        String exchange,
        String symbol,
        long generation,
        SessionHealthSnapshot health,
        long sequence,
        long messages,
        long accepted,
        long snapshots,
        long appliedUpdates,
        long rejected,
        long staleUpdates,
        long ignored,
        long published,
        double parseAvgMicros,
        double bookAvgMicros,
        RecoverySnapshot recovery,
        ProtocolSnapshot protocol,
        int bootstrapBufferEntries,
        long bootstrapBufferBytes,
        long bootstrapBufferOverflows,
        String bestBid,
        String bestAsk,
        String lastFailure
) {
}
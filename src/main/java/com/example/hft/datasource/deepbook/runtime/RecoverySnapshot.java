package com.example.hft.datasource.deepbook.runtime;


public record RecoverySnapshot(
        long reconnectAttempts,
        long reconnectSuccesses,
        long reconnectFailures,
        long recoveryDurationMillis,
        long nextBackoffMillis,
        String recoveryReason,
        boolean recoveryScheduled
) {
}

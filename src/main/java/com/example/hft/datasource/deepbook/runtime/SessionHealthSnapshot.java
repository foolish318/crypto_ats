package com.example.hft.datasource.deepbook.runtime;


public record SessionHealthSnapshot(
        TransportState transportState,
        BookState bookState,
        SessionState sessionState,
        long lastMessageTime,
        long lastAcceptedTime,
        long messageAgeMillis,
        long staleTransitions,
        String recoveryReason
) {
    public boolean publishable(long staleThresholdMillis) {
        return transportState == TransportState.CONNECTED
                && bookState == BookState.LIVE
                && sessionState == SessionState.LIVE
                && messageAgeMillis >= 0
                && messageAgeMillis < staleThresholdMillis;
    }
}

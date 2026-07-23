package com.example.hft.datasource.deepbook.runtime;


public final class SessionHealth {
    private TransportState transportState = TransportState.DISCONNECTED;
    private BookState bookState = BookState.EMPTY;
    private SessionState sessionState = SessionState.STARTING;
    private long lastMessageTime;
    private long lastAcceptedTime;
    private long staleTransitions;
    private String recoveryReason = "";

    public synchronized void connecting(boolean recovery) {
        if (sessionState == SessionState.STOPPED) {
            return;
        }
        transportState = TransportState.CONNECTING;
        sessionState = recovery ? SessionState.RECOVERING : SessionState.STARTING;
    }

    public synchronized void connected(long connectedEpochMillis) {
        if (sessionState == SessionState.STOPPED) {
            return;
        }
        transportState = TransportState.CONNECTED;
        lastMessageTime = Math.max(lastMessageTime, connectedEpochMillis);
        if (sessionState != SessionState.RECOVERING) {
            sessionState = SessionState.STARTING;
        }
    }

    public synchronized void messageReceived(long receivedEpochMillis) {
        lastMessageTime = Math.max(lastMessageTime, receivedEpochMillis);
    }

    public synchronized void bookState(BookState nextState) {
        bookState = nextState;
        if (nextState != BookState.LIVE && sessionState == SessionState.LIVE) {
            sessionState = SessionState.DEGRADED;
        }
    }

    public synchronized void accepted(long acceptedEpochMillis) {
        if (sessionState == SessionState.STOPPED) {
            return;
        }
        lastAcceptedTime = Math.max(lastAcceptedTime, acceptedEpochMillis);
        bookState = BookState.LIVE;
        if (transportState == TransportState.CONNECTED
                && sessionState != SessionState.STOPPED) {
            sessionState = SessionState.LIVE;
            recoveryReason = "";
        }
    }

    public synchronized boolean markStale(long nowEpochMillis, String reason) {
        if (sessionState == SessionState.STOPPED || bookState == BookState.STALE) {
            return false;
        }
        bookState = BookState.STALE;
        sessionState = SessionState.DEGRADED;
        recoveryReason = reason;
        staleTransitions++;

        return true;
    }

    public synchronized void recovering(String reason) {
        if (sessionState == SessionState.STOPPED) {
            return;
        }
        transportState = TransportState.DISCONNECTED;
        sessionState = SessionState.RECOVERING;
        if (bookState == BookState.LIVE) {
            bookState = BookState.DEGRADED;
        }
        recoveryReason = reason == null ? "" : reason;
    }

    public synchronized void disconnected(String reason) {
        transportState = TransportState.DISCONNECTED;
        if (sessionState != SessionState.STOPPED) {
            sessionState = SessionState.DEGRADED;
            if (bookState == BookState.LIVE) {
                bookState = BookState.DEGRADED;
            }
        }
        recoveryReason = reason == null ? "" : reason;
    }

    public synchronized void stopped() {
        transportState = TransportState.DISCONNECTED;
        sessionState = SessionState.STOPPED;
        if (bookState == BookState.LIVE) {
            bookState = BookState.DEGRADED;
        }
    }

    public synchronized SessionHealthSnapshot snapshot(
            long nowEpochMillis,
            long staleThresholdMillis
    ) {
        long age = lastMessageTime == 0L
                ? Long.MAX_VALUE
                : Math.max(0L, nowEpochMillis - lastMessageTime);
        return new SessionHealthSnapshot(
                transportState,
                bookState,
                sessionState,
                lastMessageTime,
                lastAcceptedTime,
                age,
                staleTransitions,
                recoveryReason
        );
    }

    public synchronized boolean publishable(long nowEpochMillis, long staleThresholdMillis) {
        return snapshot(nowEpochMillis, staleThresholdMillis).publishable(staleThresholdMillis);
    }
}

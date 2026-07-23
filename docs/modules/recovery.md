# Session Health And Recovery Module

![Recovery coordinator module](recovery.svg)

PNG fallback: [recovery.png](recovery.png)

Recovery is a state rebuild, not merely a socket reconnect.

## Independent States

```text
TransportState: DISCONNECTED, CONNECTING, CONNECTED
BookState:      EMPTY, BOOTSTRAPPING, LIVE, STALE, GAP_DETECTED,
                CHECKSUM_FAILED, CROSSED, DEGRADED
SessionState:   STARTING, LIVE, DEGRADED, RECOVERING, STOPPED
```

Publication requires all four predicates:

```text
transport == CONNECTED
book == LIVE
session == LIVE
last message age < stale threshold
```

Disconnect, `stop()`, expiry, and recovery immediately make publication ineligible.

## Watchdog

`StaleWatchdog` periodically reads `lastMessageTime`. When a connected source is silent beyond the configurable threshold it:

1. changes the book to `STALE`;
2. changes the session to `DEGRADED`;
3. suppresses downstream publication;
4. records the recovery reason and increments `staleTransitions`;
5. triggers reconnect/resnapshot recovery.

A later transport connection alone is insufficient. The session returns to `LIVE` only after continuous, quality-approved data is accepted.

## Recovery Coordinator

```text
failure
  -> RECOVERING and generation record
  -> jittered exponential delay
  -> new generation and connection
  -> snapshot/stream rebuild
  -> quality-approved LIVE event
  -> reset backoff
```

Backoff bases are `300ms`, `600ms`, `1.2s`, `2.4s`, and so on, capped at `30s`; jitter is applied around each base. The scheduled flag is cleared before invoking the new connection, so a fast failure can schedule the next attempt. Old generation callbacks are ignored. `stop()` cancels pending work and permanently prevents new reconnect scheduling.

Metrics: `lastMessageTime`, `lastAcceptedTime`, `messageAgeMillis`, `staleTransitions`, `recoveryReason`, `reconnectAttempts`, `reconnectSuccesses`, `reconnectFailures`, and `recoveryDurationMillis`.

# Market-Data Engine Module

![Market-data engine module](data-engine.svg)

PNG fallback: [data-engine.png](data-engine.png)

`MarketDataEngine` is the accepted-state and availability boundary.

## Accepted Path

```text
AcceptedLocalBookEvent
  -> MarketDataCache.updateAccepted
       generation fence + LIVE state
       cache by exchange + venueSymbol
  -> MarketDataEventBus
       inline deterministic listeners
       bounded asynchronous side listeners
```

Cache update happens before listeners run. A rejected old generation never reaches any listener.

## Invalidation Path

```text
BookAvailabilityEvent(STALE / RECOVERING / DISCONNECTED / INVALID / STOPPED)
  -> cache tombstone and remove latest book
  -> publish availability to every listener
  -> consolidated view removes venue
  -> strategy removes active generation
```

`MarketDataEngine.onHealth()` maps source health to domain availability, and `onError()` invalidates a known source as `INVALID`. A standalone LIVE health callback cannot clear a tombstone. Recovery requires a newer, quality-approved accepted book.

## Listener Classes

`subscribe()` is for short deterministic in-process listeners. `subscribeAsync(name, listener, capacity)` owns a bounded queue and thread for persistence, analytics, or external I/O. Metrics include queue/max depth, accepted/dropped events, last/max lag, errors, and last error. Exceptions are isolated per listener.
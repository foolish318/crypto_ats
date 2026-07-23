# Consolidated Cross-Exchange Book Module

![Consolidated book](cross-exchange-view.svg)

PNG fallback: [cross-exchange-view.png](cross-exchange-view.png)

`CrossExchangeBookView` aggregates validated venue books by `canonicalInstrumentId`. Venue symbols such as `BTCUSDT`, `BTC-USDT`, and `BTC/USD` can therefore contribute to the same instrument.

## Venue State

Each immutable `VenueBookSnapshot` retains source, exchange, venue symbol, canonical id, availability state, generation, sequence, event time, receive time, age, BBO, and depth snapshot. STALE, INVALID, DISCONNECTED, RECOVERING, STOPPED, old-generation, and freshness-expired venues are excluded.

## Consolidated Snapshot

`ConsolidatedBookSnapshot` reports:

```text
best bid + venue
best ask + venue
NBBO spread
normal / locked / crossed state
valid venue count
immutable ordered venue snapshots
event-time watermark
maximum venue skew
coherent flag
```

`coherent=false` makes time inconsistency explicit; it never presents asynchronous venue states as one synchronized observation. Availability events withdraw a venue immediately. Re-entry requires a new accepted generation, not a health-only LIVE notification.
# Architecture

Release `1.0.0` has one canonical market-data architecture.

![Canonical architecture](architecture.svg)

## Data Flow

```text
Venue REST/WebSocket
  -> RawEnvelope and checksummed journal
  -> protocol state machine
  -> source-owned single-writer local book
  -> continuity/checksum/quality gate
  -> AcceptedLocalBookEvent
  -> MarketDataEngine
  -> generation-fenced cache
  -> core and bounded asynchronous listeners
  -> consolidated book and strategy
```

Availability changes travel through the same engine boundary. `STALE`, `RECOVERING`, `DISCONNECTED`, `INVALID`, or `STOPPED` immediately tombstone the venue in cache and remove it from consolidation. An old generation cannot restore it.

## State Model

Transport state: `DISCONNECTED`, `CONNECTING`, `CONNECTED`.

Book state: `EMPTY`, `BOOTSTRAPPING`, `LIVE`, `STALE`, `GAP_DETECTED`, `CHECKSUM_FAILED`, `CROSSED`, `DEGRADED`.

Session state: `STARTING`, `LIVE`, `DEGRADED`, `RECOVERING`, `STOPPED`.

Publishing requires connected transport, live book, live session, current generation, and message age below the configured threshold. Recovery uses exponential backoff with jitter and resets only after a new generation has rebuilt a valid live book.

## Module Details

- [Source and connector](modules/source-connector.md)
- [Transport and intake](modules/transport-intake.md)
- [Parser and normalizer](modules/parser-normalizer.md)
- [Data quality](modules/data-quality.md)
- [Recovery](modules/recovery.md)
- [Local order book](modules/order-book.md)
- [Data engine](modules/data-engine.md)
- [Consolidated view](modules/cross-exchange-view.md)
- [Recorder and replay](modules/recorder-replay.md)
- [Strategy and benchmark](modules/strategy-benchmark.md)
# Module Guide

This guide describes the single current implementation (`1.0.0`).

| Area | Package | Responsibility |
|---|---|---|
| Entry points | `com.example.hft.app` | Live runner and complete replay benchmark |
| Source catalog | `datasource.deepbook` | Six public venue/symbol definitions |
| Transport | `datasource.deepbook.runtime` | Injectable WebSocket and HTTP snapshot boundaries |
| Protocol/recovery | `datasource.deepbook.runtime` | ACK/heartbeat handling, health states, watchdog, generation fencing, exponential backoff |
| Local books | `datasource.deepbook.runtime` | Venue-specific parsing, sequence/checksum rules, exact-decimal mutation, immutable snapshots |
| Journal/replay | `datasource.deepbook.runtime` | Bounded recorder, checksummed segments, index/cursor, streaming deterministic replay |
| Engine | `datasource.engine` | Availability-aware cache and isolated core/async event listeners |
| Consolidation | `datasource.deepbook.runtime` | Canonical instrument aggregation, freshness filtering, NBBO and coherence |
| Instruments | `datasource.instrument` | Venue metadata, tick/lot rules, canonical symbol mapping |
| Benchmarks | `datasource.deepbook.runtime` | JMH stages and full live-path raw replay baseline |

## Runtime Ownership

`LiveBookSession` owns one source and preserves a single writer for its mutable book. Transport and snapshot dependencies are injected through `VenueTransport` and `SnapshotProvider`, allowing deterministic failure tests. `SessionHealth`, `VenueProtocolStateMachine`, `StaleWatchdog`, and `RecoveryCoordinator` keep transport, book, and session state separate.

`BookPipeline` applies messages and calls `LocalBookPublisher` only after continuity and quality checks. `MarketDataEngine` fences updates by generation, updates `MarketDataCache`, then publishes to `MarketDataEventBus`. Core deterministic consumers run inline; slow side outputs use independently bounded worker queues.

`CrossExchangeBookView` groups venue snapshots by `canonicalInstrumentId`. Only current, fresh, `LIVE` venues participate in best bid/ask. Returned `ConsolidatedBookSnapshot` values are immutable and expose locked, crossed, venue count, watermark, and coherence.

## Adding A Source

1. Add a `DeepBookSourceDefinition` to the catalog.
2. Add or select a `LocalOrderBookBuilder` with venue sequence/checksum semantics.
3. Map its venue symbol to a canonical instrument and load metadata fail closed.
4. Add deterministic snapshot/update, gap, stale, reconnect, and replay-parity tests.
5. Run `./mvnw clean verify`, a short live smoke test, and the full replay benchmark.

Transport-specific payloads must not escape into engine or strategy contracts.
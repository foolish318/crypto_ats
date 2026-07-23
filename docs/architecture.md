# Canonical Data Pipeline Architecture

This is the single current architecture. V24 is an implementation milestone inside stable module boundaries, not a competing design.

![Canonical data pipeline](architecture.svg)

PNG fallback: [architecture.png](architecture.png)

## Current V24 Flow

```text
Binance.US REST snapshot + WebSocket diffs
OKX WebSocket snapshot + updates
Kraken WebSocket snapshot + updates
  -> VenueTransport / SnapshotProvider
  -> RawEnvelope -> bounded AsyncRawRecorder -> segmented checksummed journal
  -> VenueProtocolStateMachine
  -> BookPipeline -> source-owned LocalOrderBookBuilder
  -> continuity + quality + freshness gate
  -> AcceptedLocalBookEvent(canonicalInstrumentId, generation, immutable depth)
  -> MarketDataEngine
      -> generation-fenced MarketDataCache
      -> MarketDataEventBus
          -> inline deterministic: ConsolidatedBookView, Strategy
          -> bounded async: recorder, analytics, external I/O
```

The default remains `DIRECT_SINGLE_WRITER`: each source book has one sequential writer and no application worker queue between receive and mutation. HTTP, WebSocket, watchdog, journal, and different source callbacks can still use separate threads.

## Availability Contract

A book is publishable only when transport, book, session, and freshness are all valid. Leaving LIVE emits `BookAvailabilityEvent` immediately:

```text
LIVE -> STALE | RECOVERING | DISCONNECTED | INVALID | STOPPED
     -> generation tombstone in cache
     -> venue removed from ConsolidatedBookSnapshot
     -> strategy active-book state removed
```

A standalone health `LIVE` notification cannot restore a tombstoned book. Only an accepted snapshot/bridge plus continuity and quality checks from a permitted generation can restore publication. Late callbacks from older generations are rejected at the session, cache, and consolidated-view boundaries.

## Consolidated Book

`CrossExchangeBookView` groups venue books by `canonicalInstrumentId`, not venue symbol. Its immutable `ConsolidatedBookSnapshot` contains per-venue state, generation, sequence, event/receive time, age, BBO, and retained depth. It calculates best bid/venue, best ask/venue, NBBO spread, locked/crossed state, valid venue count, freshness exclusion, event-time watermark, maximum skew, and `coherent`.

## Listener Isolation

Core deterministic listeners execute inline in registration order. Side outputs use a bounded asynchronous channel per listener. Queue depth, maximum depth, lag, drops, errors, and last error are observable. One listener exception does not block another; queue overflow is explicit rather than unbounded buffering.

## Journal And Replay

Every `RawEnvelope` records version, record type, generation, source identity, receive clocks, payload, and lifecycle detail. `RawJournalWriter` rotates by size/time, adds a segment header and SHA-256 checksum per frame, writes an index, tracks a replay cursor, checks free disk, applies retention, and exposes durability/lag metrics.

Default durability policy:

```text
BufferedWriter flush: every 256 records
FileChannel.force(false): every 4096 records and at segment close
Allowed crash-loss window: records accepted after the latest fsync
Orderly close: drain bounded queue, flush, fsync, close, write index
```

Recorder overflow or writer failure sets `replaySafe=false`. Replay streams one line at a time into `IncrementalRawReplayProcessor`, validates checksum/segment order, and rejects unsafe or corrupt evidence. An incomplete non-newline tail is reported explicitly.

## Performance Baselines

Two different benchmarks are retained:

1. `deep-book-latency-benchmark.sh` compares direct versus source-partitioned processing capacity. It is not a complete live-pipeline benchmark.
2. `full-pipeline-benchmark.sh` exercises ingress recorder offer, protocol, parse, book mutation, quality, snapshot, engine/cache, inline listeners, and bounded async offer. It reports stage percentiles, corrected end-to-end latency, allocation, GC, lag, drops, and replay parity.
3. `jmh-deep-book.sh` isolates classification, JSON parsing, mutation, snapshot creation, publication, and cache/event fan-out with warmup and forks.

Measured evidence still supports `DIRECT_SINGLE_WRITER`; no new Disruptor, Aeron, Kafka, Chronicle, or SBE dependency was added to this path.

## Module Index

| Module | V24 responsibility | Detailed design |
|---|---|---|
| Sources/connectors | Binance.US, OKX, Kraken public depth | [source-connector.md](modules/source-connector.md) |
| Transport/intake | Injectable transport/snapshot contracts and raw ingress | [transport-intake.md](modules/transport-intake.md) |
| Parser/normalizer | Venue JSON and canonical identity | [parser-normalizer.md](modules/parser-normalizer.md) |
| Quality | Schema, sequence, checksum, crossed/freshness checks | [data-quality.md](modules/data-quality.md) |
| Local books | One ordered mutable book per source | [order-book.md](modules/order-book.md) |
| Recovery | Independent states, watchdog, generation, backoff | [recovery.md](modules/recovery.md) |
| Data engine | Cache tombstones and isolated event publication | [data-engine.md](modules/data-engine.md) |
| Recorder/replay | Segmented checksummed journal and streaming replay | [recorder-replay.md](modules/recorder-replay.md) |
| Consolidated view | Canonical NBBO, freshness, coherence/watermark | [cross-exchange-view.md](modules/cross-exchange-view.md) |
| Strategy/benchmark | Immutable inputs and two-level performance baseline | [strategy-benchmark.md](modules/strategy-benchmark.md) |

## Stable Rules

1. Strategies consume accepted immutable canonical state, never venue wire formats.
2. One mutable source book has one sequential writer.
3. Cache is generation-fenced and updated before event publication.
4. Leaving LIVE invalidates downstream state immediately.
5. Every queue is bounded and exposes overflow semantics.
6. Raw loss is visible and makes replay unsafe.
7. Replay uses the same protocol, builder, and quality semantics as live processing.
8. New concurrency or frameworks require measured end-to-end benefit and parity.
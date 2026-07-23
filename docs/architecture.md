# Canonical Data Pipeline Architecture

This is the single current architecture for the project. Version numbers are implementation milestones inside these stable module boundaries, not competing system designs.

![Canonical data pipeline](architecture.svg)

PNG fallback: [architecture.png](architecture.png)

## Reference Patterns

| Project pattern | What this project adopts |
|---|---|
| Hummingbot | A connector owns exchange lifecycle and subscriptions; order-book source, tracking, and state remain separate responsibilities. |
| NautilusTrader | Adapters normalize venue APIs into domain events; the data engine updates cache before publishing through an event bus. |
| XChange / CCXT | A common facade hides venue-specific implementations from downstream consumers. |

These are architectural patterns, not copied implementations.

## Current V23 Flow

```text
VenueInstrumentMetadataLoader -> status + tick/lot metadata (fail closed)
Binance.US REST snapshot + WebSocket diffs
OKX WebSocket snapshot + updates
Kraken WebSocket snapshot + updates
  -> connector ingress -> RawEnvelope + AsyncRawRecorder
  -> direct VenueSessionProtocol (ACK/error/heartbeat/ping/pong)
  -> LiveBookSession -> venue LocalOrderBookBuilder
  -> quality and continuity gate
  -> AcceptedLocalBookEvent
  -> MarketDataEngine
      -> MarketDataCache.deepBook(exchange, symbol)
      -> MarketDataEventBus
          -> AcceptedBookEventRecorder
          -> CrossExchangeBookView
          -> DeepBookStrategyListener
```

`REJECT`, `STALE`, `BOOTSTRAPPING`, disconnected, recovering, expired, and stopped sources do not cross the accepted-event boundary.

## V23 Direct Single-Writer Decision

The live runner no longer creates `PartitionedBookEventDispatcher`. Each WebSocket message is recorded at ingress and then parsed/applied inline to its source-owned book. This removes the application processing queue and its scheduling latency while retaining V22 protocol, metadata, recovery, bounded Binance bootstrap, and deterministic replay hardening.

`DIRECT_SINGLE_WRITER` means one mutable `exchange + symbol` book is updated sequentially. It does not claim that the JVM, HTTP client, recorder, or scheduler has only one operating-system thread.

Partitioned processing remains available only in `DeepBookReplayBenchmark`. The latest measured experiment increased saturated throughput by `2.144x`, but its queue increased end-to-end p99 from `63.42us` to `19.80ms` while current live load was roughly 121 times below direct replay capacity. The default therefore optimizes measured latency rather than unused burst capacity.

Framework research and the criteria for revisiting multithreading or distribution are recorded in [`reference-frameworks.md`](../reference-frameworks.md).

## V22 P0 Hardening

- Public instrument metadata is loaded before sessions start. A missing/inactive instrument or invalid tick/lot schema fails startup instead of guessing.
- `VenueSessionProtocol` classifies data versus control messages, requires subscription ACK for OKX/Kraken, sends venue heartbeats, detects pong/ACK timeout, and proactively rotates Binance before its 24-hour limit.
- V22 introduced and measured `PartitionedBookEventDispatcher`. V23 retains it as a capacity benchmark but removes it from the default live hot path because queue wait dominated end-to-end latency.
- Raw WebSocket evidence is recorded at connector ingress before parsing and book mutation. Recorder loss sets `replaySafe=false`; the optional partitioned benchmark also treats processing-queue overflow as replay-unsafe.
- Binance bootstrap buffering is bounded by both count and bytes. Queue, protocol, buffer, and instrument metrics are included in the run summary.
- `LOT_SIZE` remains order-entry metadata. Aggregated market-data quantities are not rejected merely because they are not an order-lot multiple; incoming prices are validated against tick size.

## State Model

Availability is the conjunction of three independent state dimensions, not a single `BookQuality.LIVE` flag:

```text
TransportState == CONNECTED
BookState      == LIVE
SessionState   == LIVE
messageAgeMillis < staleThresholdMillis
```

`StaleWatchdog` checks each connected session. Silence transitions the book to `STALE`, the session to `DEGRADED`, suppresses publication, and requests recovery. A source returns to `LIVE` only after a new connection, a valid snapshot/bridge when required, and a continuous quality-approved event.

Recovery uses generation-isolated callbacks and exponential backoff with jitter: `300ms -> 600ms -> 1.2s -> 2.4s -> ... -> 30s maximum`. Successful return to publishable `LIVE` resets the backoff.

## Replay Contract

Every raw JSONL line is a `RawEnvelope` containing version, record type, generation, source identity, both receive clocks, payload, and detail. Records cover `CONNECT`, `DISCONNECT`, `RECOVERY`, `REST_SNAPSHOT`, and `WS_MESSAGE`.

Binance REST snapshots are recorded before and after builder application. OKX and Kraken snapshots/updates retain callback order. If the bounded recorder drops anything, the run reports `replaySafe=false`, the first drop time/reason, and a `REPLAY_UNSAFE` marker; the file is then rejected by replay.

## Module Index

| Module | Current status | Detailed design |
|---|---|---|
| Sources and connectors | Binance.US, OKX, Kraken public data | [source-connector.md](modules/source-connector.md) |
| Transport and raw intake | REST/WebSocket plus immutable raw envelopes | [transport-intake.md](modules/transport-intake.md) |
| Parser and normalizer | Venue JSON to exact-decimal Java state | [parser-normalizer.md](modules/parser-normalizer.md) |
| Data quality gate | Common and venue continuity checks | [data-quality.md](modules/data-quality.md) |
| Venue-local order books | Six continuous independent books | [order-book.md](modules/order-book.md) |
| Recovery and health | Three state dimensions, watchdog, backoff, generations | [recovery.md](modules/recovery.md) |
| Market-data engine | Accepted deep-book cache and event-bus fan-out | [data-engine.md](modules/data-engine.md) |
| Recorder and replay | Full lifecycle recording and final-book parity | [recorder-replay.md](modules/recorder-replay.md) |
| Cross-exchange view | Latest accepted deep book by venue and symbol | [cross-exchange-view.md](modules/cross-exchange-view.md) |
| Strategy and benchmark | Accepted-event consumer and stage timing | [strategy-benchmark.md](modules/strategy-benchmark.md) |

## Stable Design Rules

1. Strategies consume accepted canonical state, never venue wire formats.
2. Each `exchange + symbol` owns a separate book and recovery lifecycle.
3. Exact decimal prices and quantities are retained in the source-to-book path.
4. Cache is updated before event-bus publication.
5. Raw evidence is retained without blocking book processing; any loss is explicit.
6. Replay replaces intake, not builder or validation semantics.
7. Network, parse, book, queue, processor, and end-to-end latency remain separate measurements.

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

## Current V21 Flow

```text
Binance.US REST snapshot + WebSocket diffs
OKX WebSocket snapshot + updates
Kraken WebSocket snapshot + updates
  -> RawEnvelope + AsyncRawRecorder
  -> LiveBookSession
  -> venue LocalOrderBookBuilder
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

# Canonical Data Pipeline Architecture

This is the single current architecture for the project. Version numbers are implementation milestones inside these stable module boundaries, not competing system designs.

![Canonical data pipeline](architecture.svg)

PNG fallback: [architecture.png](architecture.png)

## Reference Patterns

The layout restores the stronger reference-inspired design introduced in V15:

| Project pattern | What this project adopts |
|---|---|
| Hummingbot | A connector owns exchange lifecycle and subscriptions; order-book source, tracking, and state remain separate responsibilities. |
| NautilusTrader | Adapters normalize venue APIs into domain events; the data engine updates cache before publishing through an event bus. |
| XChange / CCXT | A common facade hides venue-specific implementations from downstream consumers. |

These are architectural patterns, not copied implementations. The Java classes in this repository remain intentionally small enough to study.

## End-To-End Flow

```text
exchange / third-party / replay source
  -> instrument provider and venue connector
  -> REST, WebSocket, FIX, or replay transport
  -> immutable RawInboundMessage
  -> venue parser and canonical normalizer
  -> data quality gate
  -> coordinator, sequencer, and venue-local order book
  -> market-data engine
  -> cache then event-bus publication
  -> cross-exchange view, strategy, recorder, and benchmark
```

`Data Quality Gate` is one module in the complete pipeline. `Local Order Books`, `Recovery`, `Recorder / Replay`, and `MarketDataEngine` are separate modules with their own contracts.

## Module Index

| Module | Status | Detailed design |
|---|---|---|
| Sources and connectors | Implemented for Binance.US, OKX, and Kraken public data | [source-connector.md](modules/source-connector.md) |
| Transport and raw intake | REST and WebSocket implemented; FIX is planned | [transport-intake.md](modules/transport-intake.md) |
| Parser and normalizer | Implemented for current venue messages | [parser-normalizer.md](modules/parser-normalizer.md) |
| Data quality gate | V20 implemented and deterministically tested | [data-quality.md](modules/data-quality.md) |
| Venue-local order books | Binance continuous path implemented; OKX/Kraken expansion remains | [order-book.md](modules/order-book.md) |
| Recovery coordinator | Binance reconnect and resnapshot implemented | [recovery.md](modules/recovery.md) |
| Market-data engine | Cache-first event publication implemented | [data-engine.md](modules/data-engine.md) |
| Recorder and replay | Recording and deterministic replay implemented | [recorder-replay.md](modules/recorder-replay.md) |
| Cross-exchange view | Top-of-book comparison implemented; deep-book view remains | [cross-exchange-view.md](modules/cross-exchange-view.md) |
| Strategy and benchmark | Java queue/pipeline decisions and stage timing implemented | [strategy-benchmark.md](modules/strategy-benchmark.md) |

The future `Order / Risk` block is shown only to preserve the system boundary. It is not documented as an implemented module.

## Failure And Recovery

```text
quality or transport failure
  -> mark only the affected venue book DEGRADED
  -> stop publishing that book
  -> reconnect and/or reload a snapshot
  -> bridge buffered updates
  -> rerun quality checks
  -> resume publication only after the book returns to LIVE
```

## Stable Design Rules

1. Strategies consume accepted canonical state, never venue wire formats.
2. Each `exchange + symbol` owns a separate book.
3. Cache is updated before event-bus publication.
4. Raw evidence is retained for replay and debugging.
5. Replay replaces the source and clock, not downstream processing contracts.
6. Network, parse, book, queue, processor, and end-to-end latency are reported separately.

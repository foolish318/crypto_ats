# Canonical Data Pipeline Architecture

This is the single current architecture for the project. Version numbers describe implementation milestones; they do not create separate competing architectures.

![Canonical data pipeline](architecture.svg)

PNG fallback:

```text
docs/architecture.png
```

## How To Read It

The main path is:

```text
market-data sources
  -> venue connectors and transport
  -> raw message capture
  -> parser and normalizer
  -> data quality gate
  -> venue-local order books
  -> market-data engine, cache, and event bus
  -> cross-exchange view, strategy, and benchmarks
```

`Data Quality Gate` is one module inside this pipeline. It does not replace the pipeline.

`Local Order Books` is another module. V17/V18 implemented the continuous Binance path. V19 added OKX and Kraken sources. V20 added shared and venue-specific quality checks. The next multi-exchange book work fills this existing block for OKX and Kraken; it does not introduce a different architecture.

## Block Responsibilities

| Block | Responsibility |
|---|---|
| Sources | Direct exchange REST/WebSocket/FIX, third-party feeds, and replay |
| Connectors | Exchange lifecycle, subscriptions, symbols, snapshots, reconnects |
| Raw intake | Preserve payload, source, receive time, and protocol metadata |
| Parser/normalizer | Convert venue messages into canonical Java events |
| Quality gate | Schema, values, freshness, sequence, checksum, crossed-book checks |
| Local books | Maintain one order book per exchange and symbol |
| Data engine | Cache accepted state and publish events to subscribers |
| Cross-exchange view | Compare separately validated venue books |
| Consumers | Strategy, replay validation, latency benchmark, and future execution |

## Failure And Recovery

```text
quality failure
  -> stop publishing the affected venue book
  -> mark it degraded
  -> reconnect or reload a snapshot
  -> rebuild and revalidate
  -> publish again only after the book returns to LIVE
```

## Module Detail Diagrams

- [Data quality module](modules/data-quality.md)
- [Local order-book module](modules/order-book.md)

These diagrams expand individual blocks from the canonical architecture. They are not alternative top-level designs.

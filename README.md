# Crypto ATS Market Data

Current release: `1.0.0`

This repository contains one reproducible Java 17 implementation of a multi-exchange depth-market-data pipeline. It builds venue-local books for Binance.US, OKX, and Kraken, publishes only healthy books, invalidates stale state immediately, creates a canonical consolidated book, and records a deterministic replay journal.

## Runtime Path

```text
REST/WebSocket
  -> RawEnvelope + segmented journal
  -> venue protocol and local order book
  -> continuity/checksum/quality gate
  -> AcceptedLocalBookEvent
  -> MarketDataEngine
  -> generation-fenced cache and event bus
  -> consolidated book and strategy
```

The realtime book path is `DIRECT_SINGLE_WRITER`: each source book is mutated sequentially by one writer. Slow side outputs use bounded asynchronous queues with explicit drop, lag, and replay-safety metrics.

## Quick Start

Requirements: JDK 17 and network access. Maven 3.9.12 is pinned by the included wrapper.

```bash
./mvnw clean verify
./scripts/run.sh 15 data 10
```

Arguments are capture duration in seconds, output directory, and stale threshold in seconds. The default source set is BTC and ETH on Binance.US, OKX, and Kraken.

Generated files are ignored by Git:

- `data/market-data-raw-<run-id>.jsonl` plus rotated segments and index
- `data/market-data-summary-<run-id>.json`
- `data/full-pipeline-*.json` and `.md`
- `data/jmh-deep-book.json`

## Documentation

- [Runbook](runbook.md)
- [Architecture](docs/architecture.md)
- [Architecture diagram](docs/architecture.svg)
- [Module guide](module.md)
- [Project structure](docs/project-structure.md)
- [Benchmark baseline](benchmark-results.md)
- [Framework decisions](reference-frameworks.md)
- [Git commands](gitcommand.md)

Historical implementations are intentionally absent from the current tree. Git history remains the audit trail.
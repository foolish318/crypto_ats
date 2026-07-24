# Crypto ATS Market Data

Current release: `1.1.0`

This repository contains one reproducible Java 17 market-data implementation for Binance.US, OKX, and Kraken. It builds generation-safe venue-local L2 books, normalizes public trades, publishes immutable strategy views, and records raw plus canonical events for deterministic replay. It contains no trading strategy, OMS, execution, position, or account logic.

## Runtime Path

```text
Depth WebSocket / REST snapshot
  -> RawEnvelope + raw journal
  -> venue protocol, sequence/checksum validation, recovery
  -> single-writer Local L2 Order Book
  -> canonical BookSnapshot + bookVersion
  -> StrategyMarketDataPort

Public Trade WebSocket
  -> venue trade normalizer + duplicate/order checks
  -> canonical PublicTrade
  -> StrategyMarketDataPort

StrategyMarketDataPort
  -> immutable OrderBookView
  -> MultiVenueBookView
  -> book/trade/status push notifications
  -> normalized event log and deterministic replay
```

The live book hot path remains `DIRECT_SINGLE_WRITER`. Each venue/instrument book has one writer. Strategies read immutable top-N snapshots; mutable tree structures never cross thread boundaries.

## Quick Start

Requirements: JDK 17 and network access. Maven 3.9.12 is pinned by the wrapper.

```bash
./mvnw clean verify
./scripts/run.sh 15 data 10
```

The default source set is BTC-USDT and ETH-USDT across Binance.US, OKX, and Kraken, with six depth streams and six public-trade streams.

Generated artifacts include:

- `data/market-data-raw-<run-id>.jsonl`: venue payloads and lifecycle evidence
- `data/market-data-normalized-<run-id>.jsonl`: canonical books, trades, and status changes
- `data/market-data-summary-<run-id>.json`: health, replay, queue, and publication metrics

## Stable Strategy Boundary

`StrategyMarketDataPort` supports push and pull without exposing adapters or mutable books:

```text
onBookUpdated / onTrade / onBookStatusChanged
getBook(venue, instrument)
getBooks(instrument)
latestTrade(venue, instrument)
```

`BookUpdated(version=N)` is emitted only after version N is visible through `getBook`. GAP, CHECKSUM_FAILED, STALE, RECOVERING, and DISCONNECTED health is visible and cannot be restored by an old stream epoch.

## Documentation

- [Runbook](runbook.md)
- [Architecture](docs/architecture.md)
- [Architecture diagram](docs/architecture.svg)
- [Module guide](module.md)
- [Project structure](docs/project-structure.md)
- [Benchmark baseline](benchmark-results.md)
- [Framework decisions](reference-frameworks.md)
- [Git commands](gitcommand.md)
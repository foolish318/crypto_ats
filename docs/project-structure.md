# Project Structure

This project remains one Maven module, with package-level boundaries that mirror a market-data trading system.

## Java Packages

```text
com.example.hft.app
  Runnable entry points called by scripts.

com.example.hft.exchange
  Shared adapter contracts and exchange-specific REST/WebSocket clients.

com.example.hft.exchange.binance
com.example.hft.exchange.okx
com.example.hft.exchange.kraken
  Venue-specific public market-data adapters and parsers.

com.example.hft.datasource
  Connector contracts, subscriptions, health/status, and module version.

com.example.hft.datasource.transport
  REST, WebSocket, FIX, third-party, replay, and raw-message metadata.

com.example.hft.datasource.normalizer
  Canonical events consumed by books and strategies.

com.example.hft.datasource.book
  Legacy Binance sequencing contracts and shared quality lifecycle enum.

com.example.hft.datasource.deepbook
  Multi-exchange deep-book source definitions and source catalog.

com.example.hft.datasource.deepbook.quality
  V20 common and venue-specific quality gates:
  sequence continuity, freshness, ordering, crossed-book, and Kraken CRC32.

com.example.hft.datasource.deepbook.runtime
  V23 direct single-writer live sessions, protocol-aware intake, exact-decimal venue builders,
  three-dimensional health, watchdog/recovery, ingress raw recording, and deterministic replay.

com.example.hft.datasource.instrument
  Instrument metadata and venue-to-canonical symbol mapping.

com.example.hft.datasource.engine
  Cache-then-publish engine, cache, and event bus.

com.example.hft.datasource.replay
  Recording and repeatable replay sources.

com.example.hft.marketdata.model
  Quotes, prices, depth updates, local books, and latency envelopes.

com.example.hft.strategy
  Quote/depth validation and trading-decision examples.

com.example.hft.pipeline
  Blocking queue, JCTools, and Disruptor processing experiments.

com.example.hft.benchmark
  Benchmark result and per-stage timing helpers.
```

## Current Deep-Book Flow

```text
Instrument metadata validation
Binance.US REST snapshot + WebSocket diff depth
OKX WebSocket books snapshot + updates
Kraken WebSocket v2 book snapshot + updates
  -> ingress RawEnvelope + AsyncRawRecorder
  -> direct protocol classifier + LiveBookSession
  -> source-owned venue builder (single writer per book)
  -> exact-decimal local book + quality gate
  -> AcceptedLocalBookEvent
  -> MarketDataEngine
  -> deep-book cache then event bus
  -> recorder, cross-exchange view, and strategy listeners
```

Rejected, stale, bootstrapping, disconnected, recovering, expired, and stopped sources do not cross the accepted-event boundary. Recovery and replay use the same venue builder rules.

## Stable Scripts

```text
scripts/run.sh                         basic Java demo
scripts/test.sh                        deterministic self-tests
scripts/benchmark.sh                   synthetic pipeline benchmark
scripts/custom-ws-vs-baseline.sh       top-of-book multi-exchange comparison
scripts/binance-depth-book.sh          Binance raw depth -> local book
scripts/binance-depth-book-30m.sh      30-minute raw depth capture
scripts/binance-depth-book-1h.sh       1-hour raw depth capture
scripts/binance-depth-book-replay.sh   deterministic depth replay
scripts/deep-book-sources.sh           V20 live quality validation
scripts/multi-exchange-local-books.sh  V23 direct single-writer local books
scripts/deep-book-latency-benchmark.sh direct vs partitioned replay latency
```

## V17 Raw Depth Book

```text
src/main/java/com/example/hft/app/BinanceRawDepthOrderBookMain.java
src/main/java/com/example/hft/datasource/book/SequencedLocalOrderBook.java
src/main/java/com/example/hft/datasource/book/DepthUpdateApplyResult.java
```

Runtime:

```text
WebSocket raw producer
  -> raw recorder
  -> parser
  -> Binance U/u sequence gate
  -> local order book
  -> book event recorder
```

## V18 Recovery

The Binance raw-depth path reconnects after WebSocket errors and reloads a REST snapshot after a gap or crossed book.

## V19 Source Catalog

```text
src/main/java/com/example/hft/datasource/deepbook/DeepBookSourceDefinition.java
src/main/java/com/example/hft/datasource/deepbook/DeepBookSourceCatalog.java
```

The catalog contains public deep-book sources for Binance.US, OKX, and Kraken.

## V20 Quality Gate

```text
src/main/java/com/example/hft/datasource/deepbook/quality/DeepBookQualityValidator.java
  Dispatches common and venue-specific checks.

src/main/java/com/example/hft/datasource/deepbook/quality/DeepBookQualityReport.java
  Separates accepted/rejected quality from transport connection state.

src/main/java/com/example/hft/datasource/deepbook/quality/KrakenBookChecksum.java
  Calculates the official top-10 asks-then-bids CRC32.

src/main/java/com/example/hft/datasource/deepbook/quality/DeepBookQualityValidatorSelfTest.java
  Covers valid feeds and injected Binance/OKX/Kraken corruption.

src/main/java/com/example/hft/app/DeepBookSourceDiscoveryMain.java
  Captures consecutive live messages and writes V20 quality evidence.
```

Documentation:

```text
docs/architecture.md
docs/architecture.svg
docs/modules/data-quality.md
docs/modules/data-quality.svg
docs/modules/order-book.md
docs/modules/order-book.svg
```

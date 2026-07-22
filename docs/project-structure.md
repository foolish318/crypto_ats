# Project Structure

This project is organized as a single Maven module with package-level boundaries. The goal is to keep the learning project simple while making the trading-system responsibilities explicit.

## Java Packages

```text
com.example.hft.app
  Runnable entry points. Scripts call these classes.

com.example.hft.exchange
  Exchange adapter interfaces, shared REST/WebSocket adapter base classes, XChange comparison client.

com.example.hft.exchange.binance
com.example.hft.exchange.okx
com.example.hft.exchange.kraken
com.example.hft.exchange.coinbase
  Exchange-specific public market-data adapters and parsers.
  Coinbase is kept as an experimental adapter; active V12 validation uses Binance.US, OKX, and Kraken.

com.example.hft.marketdata.model
  Market-data domain objects: Quote, Price, depth updates, top-of-book snapshots, payload/envelope records.

com.example.hft.marketdata.source
  Synthetic and live quote sources.

com.example.hft.strategy
  Validation and decision logic: quote processing, depth/top-N decision engines.

com.example.hft.pipeline
  Queue/ring-buffer processing implementations, latency stats, concurrent runners.

com.example.hft.benchmark
  Benchmark result and per-worker/module timing helpers.
```

## Runtime Flow

```text
Exchange adapter
  -> market-data model
  -> pipeline handoff
  -> strategy / decision engine
  -> stats / benchmark output
```

For V12 multi-exchange validation:

```text
Binance.US WebSocket depth5@100ms
OKX WebSocket books5
Kraken WebSocket book
  -> TopOfBookSnapshot
  -> REST / XChange validation comparison
```

## Scripts

Top-level scripts are stable runnable entry points. They are intentionally kept short and call Maven with one app class.

```text
scripts/run.sh                         basic demo
scripts/test.sh                        self-tests
scripts/benchmark.sh                   synthetic benchmark
scripts/custom-ws-vs-baseline.sh       current multi-exchange WebSocket validation
scripts/binance-depth-*.sh             Binance depth/live benchmark experiments
scripts/xchange-rest.sh                XChange REST experiment
```

When adding new work, prefer adding a new app class under `com.example.hft.app` and a small script that calls it.
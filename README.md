# hft-java

Java market-data practice project for low-latency trading-system concepts.

The current active public crypto data sources are:

```text
Binance.US
OKX
Kraken
```

The realtime path uses WebSocket JSON. REST and XChange are used for nearby snapshot validation, not as the main realtime path.

## Package Layout

See [docs/project-structure.md](docs/project-structure.md) for the current module boundaries.

Short version:

```text
app                 runnable entry points
exchange            adapter interfaces and exchange-specific adapters
marketdata.model    quote/depth/top-of-book data models
marketdata.source   quote/data sources
pipeline            queue/disruptor processing and latency stats
strategy            validation and decision logic
benchmark           benchmark result/timing helpers
```

## Common Commands

Run the basic demo:

```bash
./scripts/run.sh
```

Run self-tests:

```bash
./scripts/test.sh
```

Run the synthetic benchmark:

```bash
./scripts/benchmark.sh
```

Validate custom WebSocket adapters against REST/XChange baselines:

```bash
./scripts/custom-ws-vs-baseline.sh
```

Build six continuous Binance.US, OKX, and Kraken local books for a short configurable smoke run:

```bash
./scripts/multi-exchange-local-books.sh 15 data 10
```

Compile and test everything:

```bash
mvn test
mvn -q compile
./scripts/test.sh
```

Design history is recorded in `diagram.md`, setup and run commands in `runbook.md`, measured outputs in `benchmark-results.md`, data-source module design in `module.md`, the colored data-source diagram in `docs/architecture.md`, and actual image files in `docs/architecture.png` / `docs/architecture.svg`.
# hft-java

Java market-data practice project for low-latency trading-system design. The active public depth feeds are Binance.US, OKX, and Kraken.

## Current V24 Pipeline

```text
REST/WebSocket -> raw journal offer -> protocol -> venue local book
-> quality/freshness gate -> generation-fenced engine/cache
-> consolidated canonical book -> strategy
```

The default realtime mode is `DIRECT_SINGLE_WRITER`: one sequential writer per source book, with bounded asynchronous channels only for side outputs. V24 adds immediate downstream invalidation, canonical consolidated NBBO, injectable transport/snapshot boundaries, listener isolation, segmented checksummed journal replay, JMH microbenchmarks, and a full-pipeline replay benchmark.

See [docs/architecture.md](docs/architecture.md) and [docs/project-structure.md](docs/project-structure.md).

## Commands

```bash
mvn test
mvn -q compile
./scripts/test.sh

# Short live six-source smoke run: duration, output directory, stale threshold
./scripts/multi-exchange-local-books.sh 15 data 10

# Existing direct vs partitioned capacity comparison
./scripts/deep-book-latency-benchmark.sh "" 4 500000 3

# Complete production-shaped replay path, three measured runs
./scripts/full-pipeline-benchmark.sh "" 3

# JMH defaults: 3 warmups, 5 measurements, one fork, GC profiler
./scripts/jmh-deep-book.sh
```

Generated live/benchmark data is ignored by Git. Design history is in `diagram.md`, operational details in `runbook.md`, measured results in `benchmark-results.md`, data-module notes in `module.md`, and framework decisions in `reference-frameworks.md`.
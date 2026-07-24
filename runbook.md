# Runbook

This runbook applies only to release `1.1.0`.

## Reproducible Toolchain

The project requires JDK 17. The Maven Wrapper pins Maven 3.9.12 and downloads that exact distribution on first use.

```bash
java -version
./mvnw -version
./mvnw clean verify
```

A successful build runs all JUnit 5 tests and creates the application and JMH benchmark jars under `target/`.

## Run Live Market Data

```bash
./scripts/run.sh [duration-seconds] [output-directory] [stale-threshold-seconds]
```

Example:

```bash
./scripts/run.sh 15 data 10
```

Defaults are 15 seconds, `data`, and a 10-second stale threshold. The runtime connects to twelve public feeds: six depth streams and six public-trade streams.

- Binance.US `BTCUSDT`, `ETHUSDT`: REST snapshot, diff-depth, and trade WebSockets
- OKX `BTC-USDT`, `ETH-USDT`: public 400-level books and trades
- Kraken `BTC/USDT`, `ETH/USDT`: public 1000-level books and trades

The summary must be interpreted source by source. A book is publishable only when transport is `CONNECTED`, book is `LIVE`, session is `LIVE`, generation is current, and message age is below the stale threshold.

Useful summary checks:

```text
processingMode=DIRECT_SINGLE_WRITER
processingQueueEnabled=false
droppedRecords=0
replaySafe=true
replayParity=true
coreListenerErrors=0
asyncListenerDrops=0
normalizedDrops=0
normalizedReplaySafe=true
normalizedReplayParity=true
listenerErrors=0
```

Network restrictions or venue maintenance can leave an individual source unavailable without allowing old state into cache, consolidated NBBO, or strategy.

## Journal And Replay

Each run writes two evidence layers:

- `market-data-raw-<run-id>.jsonl`: venue payloads, snapshots, stream epochs, disconnects, and recovery
- `market-data-normalized-<run-id>.jsonl`: canonical BookSnapshot, PublicTrade, and BookStatusChange events accepted by StrategyMarketDataPort

The normalized recorder is bounded and nonblocking. Its replay must reproduce the same bookVersion, health, top-N state, and latest trade. Any normalized queue overflow sets `normalizedReplaySafe=false`.

The raw journal records connection lifecycle, generation changes, REST snapshots, WebSocket messages, disconnects, and recovery reasons before downstream processing. Defaults are:

- bounded recorder queue: 65,536 records
- segment rotation: 128 MiB or 15 minutes
- retention: 24 hours
- flush: every 256 records
- fsync: every 4,096 records
- minimum free disk: 64 MiB
- SHA-256 frame checksums and a segment index

The fsync interval defines the acknowledged operating-system data-loss window during a machine or kernel failure. Normal shutdown drains the queue and closes the journal. Any recorder overflow or write failure sets `replaySafe=false`; it is never reported as a complete replay.

Run the complete replay benchmark against the latest current journal:

```bash
./scripts/full-pipeline-benchmark.sh "" 3
```

Or select a journal explicitly:

```bash
./scripts/full-pipeline-benchmark.sh data/market-data-raw-<run-id>.jsonl 3
```

The benchmark uses the live path: ingress, recorder offer, parse, protocol, book mutation, quality gate, snapshot, engine, cache, event bus, and consumers. It writes JSON and Markdown results to `data/` and verifies final-book parity.

## JMH Microbenchmarks

```bash
./scripts/jmh-deep-book.sh
```

For a short validation run:

```bash
./scripts/jmh-deep-book.sh data/jmh-deep-book.json -wi 1 -i 1 -w 100ms -r 100ms -f 1
```

The default JMH settings include warmup, multiple measurements, one fork, GC profiling, JSON output, and message classification, JSON parsing, mutation, snapshot, publisher, and cache/event-bus stages.

## Tests

```bash
./scripts/test.sh
./mvnw -q compile
git diff --check
```

Important deterministic coverage includes stale/gap/checksum invalidation, generation fencing, state-before-notification, immutable multi-venue views, bookVersion, Binance/OKX/Kraken trade fixtures, duplicate and out-of-order trades, bounded recorders, raw replay, and normalized strategy-state replay parity.

## Troubleshooting

- No live data: verify outbound HTTPS/WSS access and inspect per-source recovery reason.
- `replaySafe=false`: inspect dropped-record reason, recorder queue depth, disk space, and journal write failure.
- `replayParity=false` or `normalizedReplayParity=false`: keep both journals and the summary together; do not use the run as a correctness baseline.
- Wrapper download failure: verify access to Maven Central; do not replace the pinned wrapper with an unrecorded local Maven version.

Git publication commands are maintained once in [gitcommand.md](gitcommand.md).

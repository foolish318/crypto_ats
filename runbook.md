# Java Project Runbook

`runbook.md` is the permanent setup and execution record for this project.

Rule for future work:

```text
Every new design version must add its run commands and verification steps here.
diagram.md explains design and code relationships.
runbook.md explains how to set up, run, test, and benchmark each version.
```

## Workspace

Project path in WSL:

```text
/home/yimo/hft_java
```

Shared Windows path:

```text
\\wsl.localhost\Ubuntu\home\yimo\hft_java
```

## Environment Setup

The project started as an empty directory.

Initial checks showed Java tooling was not installed:

```text
java: command not found
javac: command not found
mvn: command not found
```

Installed in WSL Ubuntu:

```bash
sudo apt-get update
sudo apt-get install -y openjdk-17-jdk maven
```

Manually requested packages:

```text
openjdk-17-jdk 17.0.19+10-1~26.04.2
maven          3.9.12-1
```

Verified tool versions:

```bash
java -version
javac -version
mvn -version
```

Observed versions:

```text
openjdk version "17.0.19" 2026-04-21
javac 17.0.19
Apache Maven 3.9.12
```

Temporary note:

```text
Before sudo installation, OpenJDK .deb packages were temporarily extracted under /tmp/hft-java-openjdk17.
That was not the final system installation path.
```

## Current Project Layout

Top-level files:

```text
.editorconfig
.gitignore
README.md
benchmark-results.md
diagram.md
pom.xml
runbook.md
scripts/benchmark.sh
scripts/run.sh
scripts/test.sh
```

Java package:

```text
src/main/java/com/example/hft
```

Main entry points:

```text
Main.java            demo application
BenchmarkMain.java   benchmark runner
SelfTestMain.java    lightweight self-test runner
```

## Common Commands

Run the demo:

```bash
./scripts/run.sh
```

Run self-tests:

```bash
./scripts/test.sh
```

Run default benchmark:

```bash
./scripts/benchmark.sh
```

Run custom benchmark:

```bash
./scripts/benchmark.sh 200000 4
```

Compile only:

```bash
mvn compile
```

Run with Maven directly:

```bash
mvn -q compile exec:java
```

Run a specific main class directly through Maven:

```bash
mvn -q -Dexec.mainClass=com.example.hft.BenchmarkMain -Dexec.args="200000 4" compile exec:java
```

## V0 Runbook: Project Bootstrap and Hello World

Status:

```text
Historical version.
```

Files used at that time:

```text
pom.xml
src/main/java/com/example/hft/Main.java
scripts/run.sh
```

How it was run:

```bash
mvn -q compile exec:java
```

Expected output at that time:

```text
Hello, world!
```

What this verified:

```text
JDK installed
Maven installed
Project structure valid
Main method runnable
```

## V1 Runbook: Simple Quote Variables in Main

Status:

```text
Historical teaching version.
```

Files used at that time:

```text
src/main/java/com/example/hft/Main.java
scripts/run.sh
```

How it was run:

```bash
./scripts/run.sh
```

Expected output style at that time:

```text
Hello, world!
Symbol: BARC.L
Bid: 1000 @ 212.4
Ask: 800 @ 212.46
Mid price: 212.43
Spread: 0.060000000000002274
```

Lesson recorded:

```text
double can show floating-point precision artifacts.
That led to the later Price class using long ticks.
```

## V2 Runbook: Domain Model and Sequential Processor

Status:

```text
Current foundation.
```

Files involved:

```text
Price.java
Quote.java
QuoteValidator.java
TradingSignal.java
TradingDecisionEngine.java
MarketDataProcessor.java
QuoteAnalysis.java
Main.java
```

How to run the demo:

```bash
./scripts/run.sh
```

Expected output style:

```text
BARC.L bid=1000@212.40 ask=800@212.46 mid=212.43 spread=0.06 signal=NEUTRAL
BARC.L bid=2500@212.39 ask=400@212.44 mid=212.41 spread=0.05 signal=BUY_PRESSURE
BARC.L bid=500@212.38 ask=3000@212.55 mid=212.46 spread=0.17 signal=DO_NOT_TRADE
```

How to test the domain rules:

```bash
./scripts/test.sh
```

Expected self-test output:

```text
self-tests passed
```

What the self-tests check:

```text
Price formatting
Price rejects zero ticks
QuoteValidator rejects locked quote
QuoteValidator rejects blank symbol
TradingDecisionEngine returns BUY_PRESSURE
TradingDecisionEngine returns SELL_PRESSURE
TradingDecisionEngine returns DO_NOT_TRADE on wide spread
```

## V3 Runbook: Shared-Queue Producer/Consumer Demo

Status:

```text
Current runnable concurrency demo through Main.java.
```

Files involved:

```text
Main.java
MarketDataFeed.java
QuoteEvent.java
QuoteMessage.java
StopMessage.java
QuoteWorker.java
ConcurrentQuoteRunner.java
ProcessingStats.java
MarketDataProcessor.java
```

How to run:

```bash
./scripts/run.sh
```

Expected output style:

```text
BARC.L bid=1000@212.40 ask=800@212.46 mid=212.43 spread=0.06 signal=NEUTRAL
BARC.L bid=2500@212.39 ask=400@212.44 mid=212.41 spread=0.05 signal=BUY_PRESSURE
BARC.L bid=500@212.38 ask=3000@212.55 mid=212.46 spread=0.17 signal=DO_NOT_TRADE
VOD.L bid=8000@71.30 ask=4500@71.32 mid=71.31 spread=0.02 signal=NEUTRAL
HSBA.L bid=600@698.40 ask=1900@698.50 mid=698.45 spread=0.10 signal=SELL_PRESSURE
stats processed=5 buyPressure=1 sellPressure=1 neutral=2 doNotTrade=1
```

What this verifies:

```text
MarketDataFeed can publish QuoteMessage events.
QuoteWorker can consume from the shared queue.
StopMessage shuts workers down.
ConcurrentQuoteRunner starts and joins threads.
ProcessingStats counts all five demo quotes.
```

Operational note:

```text
This demo keeps the data set tiny and human-readable.
Use the benchmark commands for latency comparison.
```

## V4 Runbook: Pure Java Optimization and Benchmark Suite

Status:

```text
Current benchmark suite.
```

Files involved:

```text
BenchmarkMain.java
QuoteGenerator.java
QuotePipeline.java
SequentialPipeline.java
SharedQueuePipeline.java
PartitionedQueuePipeline.java
WorkerMetrics.java
ModuleTiming.java
BenchmarkResult.java
SelfTestMain.java
```

Default benchmark command:

```bash
./scripts/benchmark.sh
```

Equivalent explicit benchmark command:

```bash
./scripts/benchmark.sh 200000 4
```

Arguments:

```text
first argument  = quote count
second argument = worker count
```

Examples:

```bash
./scripts/benchmark.sh 50000 4
./scripts/benchmark.sh 200000 4
./scripts/benchmark.sh 500000 8
```

Benchmark versions compared:

```text
v1-sequential      single-thread baseline, no queue handoff
v2-shared-queue    one producer, one shared queue, N workers
v3-partitioned     one producer, one queue per worker, symbol-hash routing
```

Measured output fields:

```text
elapsed       total wall-clock time for the whole batch
throughput    messages per second
avgE2E        average per-message end-to-end latency
p99E2E        99th percentile end-to-end latency
avgQueue      average queue wait time
avgProcessor  average validation plus decision processing time
validate      average QuoteValidator time
decision      average TradingDecisionEngine time
producerWait  total time producer spent in queue.put calls
signals       count of signal outcomes
```

Latest recorded 200,000 quote benchmark:

```text
See benchmark-results.md for full output.
```

Short interpretation:

```text
Sequential is fastest for this tiny CPU-only processor.
Shared queue adds latency mostly through queue wait and producer wait.
Partitioned queue reduces shared contention and preserves per-symbol ordering.
Validation and decision logic are measured in tens of nanoseconds and are not the bottleneck.
```

## Documentation Policy for Future Versions

When adding a new version, update both files:

```text
diagram.md
Add design, code files, relationships, data flow, strengths, weaknesses, benchmark impact, and interview talking points.

runbook.md
Add setup changes, commands, arguments, expected output, and verification steps.
```

Suggested runbook template:

```text
## Vx Runbook: Version Name

Status:
Current / Historical / Experimental

Files involved:
file list

Setup changes:
new dependencies or config

How to run:
command

How to test:
command

Expected output:
output style

Benchmark command:
command if relevant

Verification notes:
what success means
```

## V5 Runbook: JCTools Queue Pipelines

Status:

```text
Current experimental queue optimization.
```

Setup change:

```text
Added Maven dependency org.jctools:jctools-core:4.0.3 in pom.xml.
```

Files involved:

```text
pom.xml
src/main/java/com/example/hft/JctoolsSpmcQueuePipeline.java
src/main/java/com/example/hft/JctoolsSpscPartitionedPipeline.java
src/main/java/com/example/hft/BenchmarkMain.java
```

How to compile after dependency change:

```bash
mvn -q -DskipTests compile
```

How to run tests:

```bash
./scripts/test.sh
```

How to run the benchmark:

```bash
./scripts/benchmark.sh 200000 4
```

Benchmark versions now included:

```text
v1-sequential
v2-shared-queue
v3-partitioned
v5-jctools-spmc
v5-jctools-spsc-part
```

Latest result summary from 200,000 quotes and 4 workers, last measured run:

```text
v2-shared-queue       elapsed=119.63 ms avgE2E=344.59 us p99=1062.59 us avgQueue=344.44 us producerWait=106.68 ms
v3-partitioned        elapsed= 70.47 ms avgE2E= 89.82 us p99= 335.42 us avgQueue= 89.63 us producerWait= 53.85 ms
v5-jctools-spmc       elapsed= 24.40 ms avgE2E=342.69 us p99= 540.21 us avgQueue=342.49 us producerWait= 10.35 ms
v5-jctools-spsc-part  elapsed= 26.81 ms avgE2E=  8.64 us p99= 172.65 us avgQueue=  8.44 us producerWait=  8.88 ms
```

Verification notes:

```text
v5-jctools-spmc has excellent elapsed time and throughput, but average E2E latency remains high because the shared queue can accumulate backlog.
v5-jctools-spsc-part is the preferred V5 latency design because it keeps per-symbol partitioning and uses one SPSC queue per worker.
```

## V6 Runbook: Binance Live Quote Source

Status:

```text
Current live-data import version.
```

Setup changes:

```text
Added jackson-databind 2.17.2 for JSON parsing.
Uses Java 17 standard HttpClient WebSocket API.
```

Files involved:

```text
pom.xml
src/main/java/com/example/hft/QuoteSource.java
src/main/java/com/example/hft/BinanceBookTickerSource.java
src/main/java/com/example/hft/BinanceReplayMain.java
scripts/binance.sh
```

Compile:

```bash
mvn -q -DskipTests compile
```

Run live replay with defaults:

```bash
./scripts/binance.sh
```

Run live replay with explicit count and symbols:

```bash
./scripts/binance.sh 5 BTCUSDT,ETHUSDT
```

Expected output style:

```text
loaded 5 Binance bookTicker quotes from [BTCUSDT, ETHUSDT]
ETHUSDT bid=1072@1867.13 ask=640@1867.52 mid=1867.32 spread=0.39 signal=DO_NOT_TRADE
```

Operational notes:

```text
The default endpoint is Binance.US because Binance global returned HTTP 451 from this environment.
Live WebSocket access requires network availability.
This command is for data import/demo, not for pipeline latency benchmarking.
```

Official API reference:

```text
Binance.US WebSocket streams support <symbol>@bookTicker.
The payload includes s, b, B, a, A fields for symbol, best bid price/qty, and best ask price/qty.
```

## V7 Runbook: Live Binance Pipeline Latency Breakdown

Status:

```text
Current live pipeline latency measurement version.
```

Files involved:

```text
src/main/java/com/example/hft/BinanceBookTickerParser.java
src/main/java/com/example/hft/LiveQuoteEnvelope.java
src/main/java/com/example/hft/LiveLatencyStats.java
src/main/java/com/example/hft/BinanceLivePipelineMain.java
scripts/binance-latency.sh
```

Run live latency pipeline with defaults:

```bash
./scripts/binance-latency.sh
```

Run live latency pipeline with explicit count and symbols:

```bash
./scripts/binance-latency.sh 10 BTCUSDT,ETHUSDT
```

Expected output style:

```text
loaded live Binance.US bookTicker messages=10 symbols=[BTCUSDT, ETHUSDT]
networkLatency=not-measured bookTicker has no exchange event timestamp
live-latency processed=10 parseAvg=2818.40us parseP99=24842.09us queueAvg=293.78us queueP99=578.90us processorAvg=44.15us processorP99=327.00us localE2EAvg=3156.48us localE2EP99=25746.17us producerOffer=0.83ms signals[B=0 S=0 N=0 X=10]
```

Metric notes:

```text
parseAvg / parseP99 measure local JSON-to-Quote conversion.
queueAvg / queueP99 measure handoff to the live worker.
processorAvg / processorP99 measure MarketDataProcessor.signalFor.
localE2EAvg / localE2EP99 measure local WebSocket callback receipt to processing completion.
networkLatency is not measured because bookTicker has no exchange event timestamp.
```

Verification command:

```bash
mvn -q -DskipTests compile
./scripts/binance-latency.sh 10 BTCUSDT,ETHUSDT
```

## V8 Runbook: Actual Exchange-Time Pipeline

Status:

```text
Current benchmark for the user's real objective: exchange event -> local Java processing.
```

Run default actual-data benchmark:

```bash
./scripts/binance-actual-latency.sh
```

Run explicit actual-data benchmark:

```bash
./scripts/binance-actual-latency.sh 20 BTCUSDT,ETHUSDT v5-spsc 4
```

Arguments:

```text
1: message count, default 50
2: comma-separated symbols, default BTCUSDT,ETHUSDT,BNBUSDT
3: queue mode: blocking, v5-spmc, or v5-spsc; default v5-spsc
4: worker count, default 4
```

Output file:

```text
data/binance-actual-*.jsonl
```

Current preferred command:

```bash
./scripts/binance-actual-latency.sh 20 BTCUSDT,ETHUSDT v5-spsc 4
```

Metric meaning:

```text
exchToRecvAvg/P99: Binance event timestamp to local WebSocket callback receipt.
exchToDoneAvg/P99: Binance event timestamp to local processor completion.
parseAvg/P99: JSON parsing and Quote creation.
queueAvg/P99: queue handoff into worker.
processorAvg/P99: MarketDataProcessor.signalFor only.
localE2EAvg/P99: local WebSocket receipt to processor completion.
```

## V9 Runbook: Depth Order Book Pipeline

Status:

```text
Current preferred market-data benchmark for richer input volume and deeper decision logic.
```

Run default depth benchmark:

```bash
./scripts/binance-depth-latency.sh
```

Run explicit depth benchmark:

```bash
./scripts/binance-depth-latency.sh 500 BTCUSDT,ETHUSDT,BNBUSDT,SOLUSDT,XRPUSDT 4
```

Arguments:

```text
1: message count, default 200
2: comma-separated symbols, default BTCUSDT,ETHUSDT,BNBUSDT,SOLUSDT,XRPUSDT
3: worker count, default 4
```

Output file:

```text
data/binance-depth-*.jsonl
```

Metric meaning:

```text
parseAvg/P99: JSON to DepthUpdate.
bookAvg/P99: apply depth update to LocalOrderBook and extract top 10.
queueAvg/P99: v5 SPSC queue handoff.
processorAvg/P99: top 5/top 10 order-book decision logic.
localE2EAvg/P99: local WebSocket receipt to processor completion.
```

## V10 Runbook: Disruptor Framework Compare

Status:

```text
Current command for comparing v5 SPSC against v10 Disruptor on the same live depth data.
```

Run framework comparison:

```bash
./scripts/binance-depth-compare.sh 500 BTCUSDT,ETHUSDT,BNBUSDT,SOLUSDT,XRPUSDT 4
```

Arguments:

```text
1: message count, default 500
2: comma-separated symbols, default BTCUSDT,ETHUSDT,BNBUSDT,SOLUSDT,XRPUSDT
3: worker count, default 4
```

Output:

```text
One data file under data/binance-depth-compare-*.jsonl.
One metrics line for v5-depth-spsc.
One metrics line for v10-depth-disruptor.
```

Interpretation rule:

```text
Use queueAvg/P99, processorAvg/P99, and localE2EAvg/P99 to compare framework overhead.
parseAvg/bookAvg are shared upstream costs and should be equal or near-equal across both pipelines.
```

## V11 Runbook: Raw Depth Disruptor Pipeline

Status:

```text
Current command for testing Disruptor from raw market data through parse, book update, and decision.
```

Run V11:

```bash
./scripts/binance-depth-raw-disruptor.sh 500 BTCUSDT,ETHUSDT,BNBUSDT,SOLUSDT,XRPUSDT 4
```

Arguments:

```text
1: raw depth message count, default 500
2: comma-separated symbols, default BTCUSDT,ETHUSDT,BNBUSDT,SOLUSDT,XRPUSDT
3: symbol partitions, default 4
```

Output:

```text
Raw messages are stored under data/binance-raw-disruptor-*.jsonl.
Metrics are printed for v11-raw-disruptor.
```

Interpretation rule:

```text
This version measures the Disruptor-managed local pipeline: queue, parse, book, processor, and localE2E.
It should be compared against V9/V10 carefully because V11 moves parse/book work into handler stages instead of doing that work in the WebSocket callback.
```

## V12 Runbook: Custom Multi-Exchange WebSocket Adapters

Status:

```text
Current command for validating custom realtime WebSocket adapters across Binance.US, OKX, and Kraken.
```

Run validation:

```bash
./scripts/custom-ws-vs-baseline.sh
```

What it does:

```text
1. Fetches realtime top-of-book from custom WebSocket adapters.
2. Fetches nearby REST snapshots from the same exchange.
3. Fetches XChange snapshots for Binance.US and Kraken.
4. Prints bid/ask differences in raw price and basis points.
```

Interpretation:

```text
CUSTOM_WS is the realtime path.
CUSTOM REST is a snapshot validation path.
XCHANGE is a third-party baseline where configured.
OKX currently uses custom REST validation only.
```

## V13 Runbook: Package-Level Modular Refactor

Status:

```text
Current source layout for the Java project.
```

Read the package map:

```bash
cat docs/project-structure.md
```

Compile after refactor:

```bash
mvn -q compile
```

Run local smoke checks:

```bash
./scripts/test.sh
./scripts/run.sh
./scripts/benchmark.sh 1000 2
```

Current active multi-exchange validation:

```bash
./scripts/custom-ws-vs-baseline.sh
```

Main-class package change:

```text
Executable classes moved from com.example.hft.* to com.example.hft.app.*.
Scripts have been updated accordingly.
```

## Git Push Runbook: Publish Local Project to GitHub

Status:

```text
Used on 2026-07-22 to push this local project to foolish318/crypto_ats.
The push was direct to main. No PR was created.
No plaintext passwords or private keys are recorded here.
```

Repository target:

```text
git@github.com:foolish318/crypto_ats.git
```

Initial repository setup and remote inspection:

```bash
git init -b main
git remote add origin https://github.com/foolish318/crypto_ats.git
git fetch origin main
git ls-tree --name-only origin/main
git status -sb
git remote -v
git log --oneline --decorate --all -5
```

SSH authentication checks:

```bash
ssh-keygen -lf ~/.ssh/id_ed25519_github_foolish318.pub
ssh -T git@github.com
```

SSH config added because the key file had a custom name:

```text
Host github.com
    HostName github.com
    User git
    IdentityFile ~/.ssh/id_ed25519_github_foolish318
    IdentitiesOnly yes
```

Validation before commit:

```bash
grep -RInE 'password|passwd|api[_-]?key|secret|token|private key|BEGIN .*PRIVATE' --exclude-dir=.git --exclude-dir=target . || true
mvn -q compile
./scripts/test.sh
```

Commit and push commands:

```bash
git update-ref refs/heads/main origin/main
git reset --mixed origin/main
git config user.name 'foolish318'
git config user.email 'foolish318@users.noreply.github.com'
git add -A
git diff --cached --stat
git diff --cached --name-only | wc -l
git commit -m 'Organize crypto ATS Java project'
git remote set-url origin git@github.com:foolish318/crypto_ats.git
git push -u origin main
```

Final verification commands:

```bash
git status -sb
git log --oneline --decorate -3
git remote -v
```

Result:

```text
Pushed commit 146d9e9 Organize crypto ATS Java project.
main now tracks origin/main.
Working tree was clean after push.
```

## V14 Runbook: Data Source Module Refactor

Status:

```text
Adds the datasource package and refactors the multi-exchange validation app to use MarketDataConnector.
```

Read the data-source design:

```bash
cat module.md
cat docs/architecture.md
```

Compile after refactor:

```bash
mvn -q compile
```

Run local smoke checks:

```bash
./scripts/test.sh
```

Run active multi-exchange validation through the datasource connector wrapper:

```bash
./scripts/custom-ws-vs-baseline.sh
```

Current important files:

```text
src/main/java/com/example/hft/datasource/MarketDataConnector.java
src/main/java/com/example/hft/datasource/TopOfBookMarketDataConnector.java
src/main/java/com/example/hft/datasource/transport/RawInboundMessage.java
src/main/java/com/example/hft/datasource/normalizer/NormalizedMarketDataEvent.java
src/main/java/com/example/hft/app/CustomWebSocketVsBaselineTopOfBookMain.java
docs/architecture.md
module.md
```
## V15 Runbook: Reference-Inspired Data Module Cleanup

Status:

```text
Adds instrument metadata, data engine/cache/event bus, replay skeletons, and a cleaner architecture image.
```

Read the updated data-module design:

```bash
cat module.md
cat docs/project-structure.md
```

Open the real architecture images:

```text
docs/architecture.png
docs/architecture.svg
```

Compile and smoke test:

```bash
mvn -q compile
./scripts/test.sh
```

Relevant new packages:

```text
src/main/java/com/example/hft/datasource/instrument
src/main/java/com/example/hft/datasource/engine
src/main/java/com/example/hft/datasource/replay
```
## V16 Runbook: Data Engine Runtime Wiring

Status:

```text
The active multi-exchange top-of-book validation app now routes WebSocket data through the datasource engine/cache/bus/recorder path.
```

Compile and run self-tests:

```bash
mvn -q compile
./scripts/test.sh
```

Run the active architecture path:

```bash
./scripts/custom-ws-vs-baseline.sh
```

Expected output markers:

```text
datasource-engine-websocket-vs-baseline version=V16-data-engine-runtime sampledAt=...
CUSTOM_WS ... canonical=... connectorStatus=LIVE
DATASOURCE_ENGINE_SUMMARY version=V16-data-engine-runtime cacheTopOfBook=6 publishedEvents=6 replayRecords=6 eventBusListeners=1`nDATASOURCE_VALIDATION_SUMMARY version=V16-data-engine-runtime connectors=6 ... avgWsLoadMs=... avgEngineEtlUs=...
```

Relevant code:

```text
src/main/java/com/example/hft/app/CustomWebSocketVsBaselineTopOfBookMain.java
src/main/java/com/example/hft/datasource/FanoutMarketDataSink.java
src/main/java/com/example/hft/datasource/engine/MarketDataEngine.java
src/main/java/com/example/hft/datasource/engine/MarketDataCache.java
src/main/java/com/example/hft/datasource/engine/MarketDataEventBus.java
src/main/java/com/example/hft/datasource/replay/RecordingMarketDataSink.java
src/main/java/com/example/hft/datasource/instrument/SymbolMapper.java
```
## V16 Runbook: Compare Data Source Versions

Purpose:

```text
Compare the previous connector-wrapper version with the current data-engine runtime path.
This is a live smoke benchmark. Treat network-included load time as directional only.
```

Run current V16 and save a log:

```bash
./scripts/custom-ws-vs-baseline.sh | tee data/datasource-v16-current-latest.log
```

Run previous V14 connector-wrapper commit in a temporary worktree:

```bash
git worktree add --detach /tmp/hft_java_prev_f584843 f584843
cd /tmp/hft_java_prev_f584843
./scripts/custom-ws-vs-baseline.sh | tee /home/yimo/hft_java/data/datasource-v14-prev-f584843-latest.log
cd /home/yimo/hft_java
git worktree remove --force /tmp/hft_java_prev_f584843
```

Summarize load/ETL timing and quality:

```bash
scripts/compare-datasource-logs.py \
  data/datasource-v14-prev-f584843-latest.log \
  data/datasource-v16-current-latest.log \
  | tee data/datasource-version-comparison-latest.csv
```

Latest comparison result:

```text
V14 avgWsLoadMs=1329.626667 avgEngineEtlUs=0.000000 restExact=5/6 xchangeExact=3/4
V16 avgWsLoadMs=2108.258333 avgEngineEtlUs=433.300000 restExact=6/6 xchangeExact=4/4 cacheTopOfBook=6 publishedEvents=6 replayRecords=6
```

Reading:

```text
V16 validates the architecture wiring: cache, event bus, and replay recorder all saw the same 6 top-of-book events.
The clean latency benchmark still needs replay, because live WebSocket load includes network and exchange timing that changes between runs.
```
## V17 Runbook: Raw Depth To Local Order Book

Purpose:

```text
Capture unsampled Binance.US depth diff messages, build local order books from raw data, and keep replayable files for later 30m/1h backtests.
```

Compile:

```bash
mvn -q compile
```

Short smoke test:

```bash
./scripts/binance-depth-book.sh 20 BTCUSDT,ETHUSDT,BNBUSDT,SOLUSDT,XRPUSDT 10 data
```

30-minute live capture:

```bash
./scripts/binance-depth-book-30m.sh BTCUSDT,ETHUSDT,BNBUSDT,SOLUSDT,XRPUSDT 10 data
```

1-hour live capture:

```bash
./scripts/binance-depth-book-1h.sh BTCUSDT,ETHUSDT,BNBUSDT,SOLUSDT,XRPUSDT 10 data
```

Replay a saved capture:

```bash
./scripts/binance-depth-book-replay.sh \
  data/binance-raw-depth-v17-<run-id>.jsonl \
  data/binance-depth-snapshots-v17-<run-id>.jsonl \
  10
```

Generated files:

```text
data/binance-raw-depth-v17-<run-id>.jsonl          raw WebSocket payloads, no local sampling
data/binance-depth-snapshots-v17-<run-id>.jsonl    REST snapshots used to anchor the books
data/binance-book-events-v17-<run-id>.jsonl        per-message book apply result and top levels
data/binance-book-summary-v17-<run-id>.json        final quality and latency summary
```

Latest 20-second smoke result:

```text
BINANCE_RAW_DEPTH_BOOK_SUMMARY version=V17-raw-depth-to-order-book durationSeconds=20 rawMessages=222 parsed=222 parseFailures=0 applied=217 stale=5 gaps=0 crossed=0 unknownSymbol=0 parseAvgUs=116.60 bookAvgUs=47.49 localE2EAvgUs=18278.96 exchangeToReceiveAvgUs=34662.16
BTCUSDT LIVE applied=50 stale=2 gaps=0 crossed=0
ETHUSDT LIVE applied=78 stale=1 gaps=0 crossed=0
BNBUSDT LIVE applied=24 stale=2 gaps=0 crossed=0
SOLUSDT LIVE applied=38 stale=0 gaps=0 crossed=0
XRPUSDT LIVE applied=27 stale=0 gaps=0 crossed=0
```

Latest replay validation of that same capture:

```text
BINANCE_RAW_DEPTH_REPLAY_SUMMARY rawMessages=222 parsed=222 parseFailures=0 applied=217 stale=5 gaps=0 crossed=0 unknownSymbol=0
Replay rebuilt the same final lastUpdateId and top-of-book state for all five symbols.
```

Git note:

```text
Generated V17 market-data captures are ignored by .gitignore. Keep code, docs, scripts, and diagrams in Git; regenerate raw data locally when benchmarking.
```
## V18 Runbook: Automatic Reconnect And Resync

Purpose:

```text
First production hardening step for the Binance.US raw-depth local book path.
```

What changed:

```text
WebSocket onError no longer immediately aborts the run.
The app reconnects the Binance.US depth stream and reloads snapshots for all active symbols.
If a local book detects GAP or CROSSED, the app reloads a REST depth snapshot for that symbol and continues.
Snapshot JSONL lines now include reason: INITIAL, WEBSOCKET_RECONNECT, GAP, or CROSSED.
Summary output now includes resyncAttempts/resyncSuccesses/resyncFailures and reconnectAttempts/reconnectSuccesses/reconnectFailures.
```

Run live smoke:

```bash
./scripts/binance-depth-book.sh 20 BTCUSDT,ETHUSDT,BNBUSDT,SOLUSDT,XRPUSDT 10 data
```

Latest V18 smoke result:

```text
BINANCE_RAW_DEPTH_BOOK_SUMMARY version=V18-auto-resync-order-book durationSeconds=20 rawMessages=248 parsed=248 parseFailures=0 applied=243 stale=5 gaps=0 crossed=0 unknownSymbol=0 resyncAttempts=0 resyncSuccesses=0 resyncFailures=0 reconnectAttempts=0 reconnectSuccesses=0 reconnectFailures=0
```

Interpretation:

```text
No reconnect/resync was needed during this 20-second clean live run.
The deterministic self-test now covers gap -> snapshot reload -> bridged update -> LIVE quality.
```
## V19 Runbook: Multi-Exchange Deep Book Sources

Purpose:

```text
Add the second production hardening item: more real deep-book data sources, not just Binance.US.
```

Run live source validation:

```bash
./scripts/deep-book-sources.sh data
```

Current source catalog:

```text
Binance.US BTCUSDT  REST depth snapshot limit=5000 + WebSocket depth@100ms
Binance.US ETHUSDT  REST depth snapshot limit=5000 + WebSocket depth@100ms
OKX BTC-USDT        public WebSocket books, 400 levels
OKX ETH-USDT        public WebSocket books, 400 levels
Kraken BTC/USD      public WebSocket v2 book, 1000 levels
Kraken ETH/USD      public WebSocket v2 book, 1000 levels
```

Latest live validation:

```text
sources=6 successes=6 failures=0
Binance.US BTCUSDT snapshotBidLevels=1452 snapshotAskLevels=2653 updateBidLevels=2 updateAskLevels=1
Binance.US ETHUSDT snapshotBidLevels=567 snapshotAskLevels=1643 updateBidLevels=14 updateAskLevels=12
OKX BTC-USDT snapshotBidLevels=400 snapshotAskLevels=400 seqId=79099752011 prevSeqId=-1
OKX ETH-USDT snapshotBidLevels=400 snapshotAskLevels=400 seqId=72393794762 prevSeqId=-1
Kraken BTC/USD snapshotBidLevels=1000 snapshotAskLevels=1000 checksum=1408918792
Kraken ETH/USD snapshotBidLevels=1000 snapshotAskLevels=1000 checksum=954289141
```

Interpretation:

```text
V19 proves the project can now discover and validate multiple public deep-book feeds.
This is source onboarding only. It does not yet maintain OKX/Kraken local books with full sequence/checksum rules; that belongs to the next quality-hardening step.
```
## V19 Git Commands Used

```bash
git status --short
git add .gitignore diagram.md docs/project-structure.md module.md runbook.md scripts/deep-book-sources.sh src/main/java/com/example/hft/app/DeepBookSourceDiscoveryMain.java src/main/java/com/example/hft/datasource/DataSourceModuleVersion.java src/main/java/com/example/hft/datasource/deepbook/DeepBookSourceCatalog.java src/main/java/com/example/hft/datasource/deepbook/DeepBookSourceDefinition.java
git commit -m "Add multi-exchange deep book sources"
git push origin main
```
## Git Command Reference

Git push commands are centralized in `gitcommand.md`. Future version notes should reference that file instead of repeating long git command blocks.
## V20 Runbook: Multi-Exchange Data Quality Gate

Purpose:

```text
Integrate venue-specific data quality checks with the V19 Binance.US, OKX, and Kraken deep-book sources.
```

Compile and run deterministic tests:

```bash
mvn -q compile
./scripts/test.sh
```

Run the live quality probe:

```bash
./scripts/deep-book-sources.sh data
```

Expected summary shape:

```text
DEEP_BOOK_SOURCE_SUMMARY version=V20-deep-book-quality-gate sources=6 connected=6 qualityAccepted=6 rejected=0
```

Generated evidence:

```text
data/deep-book-quality-v20-<run-id>.jsonl
```

Important fields:

```text
transportSuccess
qualityAccepted
qualityCheckedMessages
qualityChecksPassed
qualityChecksFailed
qualityFailures
qualitySequence
qualityChecksum
```

Architecture and module documentation:

```text
docs/architecture.md
docs/modules/data-quality.md
docs/modules/order-book.md
```

Git commands remain centralized in `gitcommand.md`.
## V21 Runbook: Accepted Multi-Exchange Local Books

Purpose:

```text
Run six Binance.US, OKX, and Kraken source-to-local-book sessions concurrently.
Process every received book message without application sampling.
Publish only fresh, quality-approved books through MarketDataEngine.
Record enough raw lifecycle evidence for deterministic reconstruction.
```

Required verification:

```bash
mvn test
mvn -q compile
./scripts/test.sh
```

Run with defaults (`15s` smoke window, `data`, `10s` stale threshold):

```bash
./scripts/multi-exchange-local-books.sh
```

Run with explicit values:

```bash
./scripts/multi-exchange-local-books.sh 12 data 10
```

Arguments:

```text
1: duration in seconds, default 15
2: output directory, default data
3: no-message stale threshold in seconds, default 10
```

The duration is only a smoke-test window. It is not a 30-minute connection or retention requirement.

Generated files:

```text
data/multi-exchange-raw-v21-<run-id>.jsonl
  RawEnvelope records for REST_SNAPSHOT, WS_MESSAGE, CONNECT, DISCONNECT,
  and RECOVERY, including generation and receive clocks.

data/multi-exchange-books-v21-<run-id>.json
  Session states, health/recovery counters, latency, final books, recorder safety,
  engine/event-bus counts, and live-vs-replay parity.
```

Important success fields:

```text
publishableBooksAtRunEnd == configured source count
droppedRecords == 0
replaySafe == true
replayParity == true
finalSessionState == STOPPED
```

A run may legitimately be degraded because of exchange/network conditions. In that case inspect `transportState`, `bookState`, `sessionState`, `messageAgeMillis`, `recoveryReason`, reconnect counters, and `lastFailure` per source.

Latest validation on 2026-07-23:

```text
sources=6 publishableBooks=6 messages=984 published=965 rejected=0
reconnectAttempts=0 deepBookCache=6 eventRecorder=965 crossExchangeView=6
recordedRecords=1006 droppedRecords=0 replaySafe=true replayParity=true
JUnit tests=11 failures=0; scripts/test.sh=self-tests passed
```

Git commands remain centralized in `gitcommand.md`.

## V22 Runbook: P0-Hardened Local Books

V22 keeps the V21 engine and six venue-local books, then adds production-critical intake controls:

```text
public instrument metadata (status, tick, lot)
  -> REST/WebSocket connector ingress
  -> RawEnvelope recorded before asynchronous handoff
  -> source-affine PartitionedBookEventDispatcher (4 bounded workers)
  -> venue protocol gate (ACK/error/heartbeat/ping/pong)
  -> LiveBookSession + venue builder + quality gate
  -> AcceptedLocalBookEvent -> cache -> event bus
```

Run live validation:

```bash
./scripts/multi-exchange-local-books.sh 10 data 10
```

Expected V22 files:

```text
data/multi-exchange-raw-v22-<run-id>.jsonl
data/multi-exchange-books-v22-<run-id>.json
```

Important P0 fields:

```text
instrumentMetadataCount == source count
subscriptionAcknowledged == true for live subscribed sources
protocolErrors == 0
bootstrapBufferOverflows == 0
dispatcherQueueFullRejections == 0
dispatcherTaskFailures == 0
droppedRecords == 0
replaySafe == true
replayParity == true
```

Latest live validation on 2026-07-23:

```text
sources=6 publishableBooks=6 messages=4406 published=4380 rejected=0
reconnectAttempts=0 dispatcherWorkers=4 dispatcherQueueFull=0 dispatcherMaxDepth=81
dispatcherQueueAvg=818.10us dispatcherProcessingAvg=166.57us
recordedRecords=4428 droppedRecords=0 replaySafe=true replayParity=true
JUnit tests=22 failures=0; scripts/test.sh=self-tests passed
```

Run the real-data replay benchmark. The script uses the newest V23/V22/V21 raw file when the first argument is omitted:

```bash
./scripts/deep-book-latency-benchmark.sh "" 4 500000 3
```

Arguments:

```text
1: raw JSONL path, empty selects newest file
2: worker count, default 4
3: minimum measured records per run, default 500000
4: repeated runs, default 5
5: optional JSON result path
```

Final V22 result using `multi-exchange-raw-v22-2026-07-23T133841929141429Z.jsonl`:

```text
records per run:                 500364
book parity:                     true
direct median throughput:        68466 events/s
4-worker median throughput:      136199 events/s
throughput speedup:              1.989x
direct processing p99:           65.36us
4-worker processing p99:         56.55us
direct end-to-end p99:           65.36us
4-worker end-to-end p99:         22720.66us
```

Interpretation: source-partitioned concurrency nearly doubled saturated replay throughput and slightly improved processor p99. It did not reduce end-to-end latency under an unpaced burst because the bounded queues accumulated backlog. For latency-sensitive operation, track queue wait and backpressure independently, keep each source single-writer, and size or pace intake so queues remain near empty.

The benchmark excludes JSONL file reading from timed processing and replays identical real exchange records. It is a project regression benchmark, not a replacement for forked JMH or production hardware testing.

## V23 Runbook: Direct Single-Writer Live Path

V23 keeps every V22 protocol, metadata, quality, recovery, bounded-bootstrap, and replay-safety control. It changes only the default processing handoff: the live runner no longer creates `PartitionedBookEventDispatcher`.

```text
exchange callback
  -> RawEnvelope + asynchronous persistence
  -> inline venue protocol classification
  -> source-owned LiveBookSession
  -> source-owned LocalOrderBookBuilder
  -> quality gate
  -> accepted event engine/cache/event bus/strategy
```

### What "single-writer" means

The whole JVM is not restricted to one thread. `HttpClient`, WebSocket connections, the scheduler, watchdog, and recorder can use other threads, and separate sources may receive callbacks concurrently. The invariant is narrower and more important: one mutable `exchange + symbol` order book is updated sequentially by one callback path, with no application worker queue between receive and apply.

### Why the default does not use the four processing workers

The latest identical-record V23 benchmark measured:

```text
observed live public-feed rate:       about 580 events/s
direct median replay throughput:      70,028 events/s
four-worker replay throughput:         150,140 events/s
direct end-to-end p99:                63.42 us
four-worker queue p99:                 19,786.19 us
four-worker end-to-end p99:            19,801.80 us
direct capacity / observed live rate: about 121x
```

The workers improved saturated aggregate throughput by `2.144x`, but the queue dominated tail latency and increased end-to-end p99 by `317.270x`. With roughly 121 times direct-path headroom, that additional burst capacity is currently unused. A lower single-event latency, deterministic ownership, and a smaller failure surface are more valuable for this live market-data path.

This is not a claim that multithreading is always slower. Partitioning should return when measured direct CPU saturation, a much larger symbol set, heavier independent calculations, or callback interference makes aggregate capacity a real constraint. Acceptance requires identical records, final-book parity, and an explicit latency or throughput budget; throughput alone is not enough.

Framework and distribution options are compared in [`reference-frameworks.md`](reference-frameworks.md). The optional partitioned implementation remains in the repository as a capacity benchmark; it is not used by the default live command.

### Run V23

```bash
./scripts/multi-exchange-local-books.sh 10 data 10
```

Expected files:

```text
data/multi-exchange-raw-v23-<run-id>.jsonl
data/multi-exchange-books-v23-<run-id>.json
```

Expected summary fields:

```text
version=V23-direct-single-writer-hot-path
processingMode=DIRECT_SINGLE_WRITER
processingQueue=false
droppedRecords=0
replaySafe=true
replayParity=true
```

The duration argument is only the smoke-test capture window. It is not a requirement to keep a production connection open for 30 or 60 minutes.
Latest V23 live validation on 2026-07-23:

```text
sources=6 publishableBooks=6 messages=5804 published=5779 rejected=0
processingMode=DIRECT_SINGLE_WRITER processingQueue=false reconnectAttempts=0
deepBookCache=6 eventRecorder=5779 crossExchangeView=6 strategyEvents=5779
recordedRecords=5826 droppedRecords=0 replaySafe=true replayParity=true
```

### Optional capacity experiment

```bash
./scripts/deep-book-latency-benchmark.sh "" 4 500000 3
```

This replays the newest V23/V22/V21 capture through both direct and source-partitioned modes. Keep `processing p99`, `queue p99`, `end-to-end p99`, throughput, and final-book parity separate when interpreting the result.

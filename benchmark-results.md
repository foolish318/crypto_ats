# Benchmark Results

## Latest V5 Benchmark

Command:

```bash
./scripts/benchmark.sh 200000 4
```

Environment note:

```text
This is a simple in-process Java benchmark, not a JMH benchmark. Treat the numbers as directional for comparing our designs, not as final production performance claims.
```

Latest measured output:

```text
benchmark quotes=200000 workers=4 warmups=1 runs=3
run 1
v1-sequential       workers=1 count=200000 elapsed=  42.39 ms throughput=   4718134 msg/s avgE2E=   0.16 us p99E2E=   0.19 us avgQueue=   0.00 us avgProcessor=   0.16 us validate=  41.7 ns decision=  43.6 ns producerWait=   0.00 ms signals[B=24500 S=24380 N=51120 X=100000]
v2-shared-queue     workers=4 count=200000 elapsed= 152.51 ms throughput=   1311412 msg/s avgE2E= 289.32 us p99E2E=1051.24 us avgQueue= 289.15 us avgProcessor=   0.17 us validate=  43.6 ns decision=  41.1 ns producerWait= 140.61 ms signals[B=24500 S=24380 N=51120 X=100000]
v3-partitioned      workers=4 count=200000 elapsed=  97.10 ms throughput=   2059759 msg/s avgE2E= 110.23 us p99E2E= 351.49 us avgQueue= 110.04 us avgProcessor=   0.20 us validate=  60.5 ns decision=  45.3 ns producerWait=  81.04 ms signals[B=24500 S=24380 N=51120 X=100000]
v5-jctools-spmc     workers=4 count=200000 elapsed=  44.54 ms throughput=   4490840 msg/s avgE2E= 209.21 us p99E2E=1245.19 us avgQueue= 208.99 us avgProcessor=   0.22 us validate=  55.4 ns decision=  43.9 ns producerWait=  32.08 ms signals[B=24500 S=24380 N=51120 X=100000]
v5-jctools-spsc-part workers=4 count=200000 elapsed=  56.62 ms throughput=   3532098 msg/s avgE2E=  24.10 us p99E2E= 729.48 us avgQueue=  23.90 us avgProcessor=   0.20 us validate=  50.5 ns decision=  41.6 ns producerWait=  15.27 ms signals[B=24500 S=24380 N=51120 X=100000]
run 2
v1-sequential       workers=1 count=200000 elapsed=  32.78 ms throughput=   6100827 msg/s avgE2E=   0.12 us p99E2E=   0.15 us avgQueue=   0.00 us avgProcessor=   0.12 us validate=  31.3 ns decision=  33.8 ns producerWait=   0.00 ms signals[B=24500 S=24380 N=51120 X=100000]
v2-shared-queue     workers=4 count=200000 elapsed= 123.45 ms throughput=   1620094 msg/s avgE2E= 370.88 us p99E2E=1193.16 us avgQueue= 370.71 us avgProcessor=   0.17 us validate=  47.9 ns decision=  41.0 ns producerWait= 108.15 ms signals[B=24500 S=24380 N=51120 X=100000]
v3-partitioned      workers=4 count=200000 elapsed=  80.50 ms throughput=   2484445 msg/s avgE2E= 113.85 us p99E2E= 325.41 us avgQueue= 113.65 us avgProcessor=   0.20 us validate=  70.7 ns decision=  45.7 ns producerWait=  62.08 ms signals[B=24500 S=24380 N=51120 X=100000]
v5-jctools-spmc     workers=4 count=200000 elapsed=  25.81 ms throughput=   7747848 msg/s avgE2E= 415.83 us p99E2E= 534.26 us avgQueue= 415.63 us avgProcessor=   0.20 us validate=  77.5 ns decision=  43.9 ns producerWait=  10.87 ms signals[B=24500 S=24380 N=51120 X=100000]
v5-jctools-spsc-part workers=4 count=200000 elapsed=  23.62 ms throughput=   8465981 msg/s avgE2E=   9.88 us p99E2E= 184.11 us avgQueue=   9.70 us avgProcessor=   0.18 us validate=  60.9 ns decision=  43.5 ns producerWait=   8.72 ms signals[B=24500 S=24380 N=51120 X=100000]
run 3
v1-sequential       workers=1 count=200000 elapsed=  43.98 ms throughput=   4547184 msg/s avgE2E=   0.17 us p99E2E=   0.21 us avgQueue=   0.00 us avgProcessor=   0.17 us validate=  43.9 ns decision=  41.0 ns producerWait=   0.00 ms signals[B=24500 S=24380 N=51120 X=100000]
v2-shared-queue     workers=4 count=200000 elapsed= 119.63 ms throughput=   1671755 msg/s avgE2E= 344.59 us p99E2E=1062.59 us avgQueue= 344.44 us avgProcessor=   0.15 us validate=  42.5 ns decision=  38.3 ns producerWait= 106.68 ms signals[B=24500 S=24380 N=51120 X=100000]
v3-partitioned      workers=4 count=200000 elapsed=  70.47 ms throughput=   2838061 msg/s avgE2E=  89.82 us p99E2E= 335.42 us avgQueue=  89.63 us avgProcessor=   0.19 us validate=  66.5 ns decision=  42.6 ns producerWait=  53.85 ms signals[B=24500 S=24380 N=51120 X=100000]
v5-jctools-spmc     workers=4 count=200000 elapsed=  24.40 ms throughput=   8196156 msg/s avgE2E= 342.69 us p99E2E= 540.21 us avgQueue= 342.49 us avgProcessor=   0.20 us validate=  73.0 ns decision=  42.8 ns producerWait=  10.35 ms signals[B=24500 S=24380 N=51120 X=100000]
v5-jctools-spsc-part workers=4 count=200000 elapsed=  26.81 ms throughput=   7458902 msg/s avgE2E=   8.64 us p99E2E= 172.65 us avgQueue=   8.44 us avgProcessor=   0.20 us validate=  69.3 ns decision=  43.7 ns producerWait=   8.88 ms signals[B=24500 S=24380 N=51120 X=100000]
module deltas from last measured run
v1-sequential       delta vs v1-sequential queue=   +0.0 ns processor=   +0.0 ns validation=   +0.0 ns decision=   +0.0 ns
v2-shared-queue     delta vs v1-sequential queue=+344439.1 ns processor=  -21.3 ns validation=   -1.4 ns decision=   -2.7 ns
v3-partitioned      delta vs v1-sequential queue=+89632.0 ns processor=  +16.4 ns validation=  +22.6 ns decision=   +1.6 ns
v5-jctools-spmc     delta vs v1-sequential queue=+342491.5 ns processor=  +25.8 ns validation=  +29.1 ns decision=   +1.8 ns
v5-jctools-spsc-part delta vs v1-sequential queue=+8443.3 ns processor=  +27.5 ns validation=  +25.4 ns decision=   +2.7 ns
```

## V5 Interpretation

```text
v5-jctools-spmc has the best elapsed time in the last measured run, but its average E2E latency remains high because one shared queue can accumulate backlog.

v5-jctools-spsc-part is the preferred low-latency design. It preserves per-symbol partitioning and maps each partition to one SPSC queue and one worker.
```

Improvement versus V3 partitioned ArrayBlockingQueue, last measured run:

```text
elapsed:      70.47 ms -> 26.81 ms  about 62.0% lower
avgE2E:       89.82 us ->  8.64 us  about 90.4% lower
p99E2E:      335.42 us ->172.65 us  about 48.5% lower
avgQueue:     89.63 us ->  8.44 us  about 90.6% lower
producerWait: 53.85 ms ->  8.88 ms  about 83.5% lower
```

Improvement versus V2 shared ArrayBlockingQueue, last measured run:

```text
elapsed:     119.63 ms -> 26.81 ms  about 77.6% lower
avgE2E:      344.59 us ->  8.64 us  about 97.5% lower
p99E2E:     1062.59 us ->172.65 us  about 83.8% lower
avgQueue:    344.44 us ->  8.44 us  about 97.5% lower
producerWait:106.68 ms ->  8.88 ms  about 91.7% lower
```

## V8 Actual Binance.US Ticker Result

Command:

```bash
./scripts/binance-actual-latency.sh 20 BTCUSDT,ETHUSDT v5-spsc 4
```

Output:

```text
stored actual Binance.US ticker messages=20 file=data/binance-actual-2026-07-19T022825.305155026Z.jsonl
v5-spsc processed=20 exchToRecvAvg=420550.00us exchToRecvP99=1078000.00us exchToDoneAvg=421274.49us exchToDoneP99=1078259.52us parseAvg=675.74us parseP99=9284.97us queueAvg=22.54us queueP99=122.11us processorAvg=21.64us processorP99=265.06us localE2EAvg=724.49us localE2EP99=9747.45us producerOffer=0.22ms signals[B=0 S=0 N=0 X=20]
```

Current reading:

```text
For this actual-data run, v5-spsc processor time was 21.64us average and 265.06us p99.
The larger end-to-end number is dominated by exchange-to-local arrival time and occasional parsing spikes, not by MarketDataProcessor itself.
```

## V9 Depth Binance.US Result

Command:

```bash
./scripts/binance-depth-latency.sh 500 BTCUSDT,ETHUSDT,BNBUSDT,SOLUSDT,XRPUSDT 4
```

Output:

```text
stored actual Binance.US depth messages=500 file=data/binance-depth-2026-07-19T024726.864303716Z.jsonl
v9-depth-v5-spsc processed=500 exchToRecvAvg=-3152.00us exchToRecvP99=93000.00us exchToDoneAvg=-3010.15us exchToDoneP99=93084.52us parseAvg=98.81us parseP99=313.40us bookAvg=20.25us bookP99=147.59us queueAvg=12.77us queueP99=139.46us processorAvg=6.90us processorP99=29.16us localE2EAvg=141.85us localE2EP99=533.03us producerOffer=2.52ms signals[B=9 S=47 N=152 X=292]
```

Current reading:

```text
V9 uses real depth updates, local order-book maintenance, and top 5/top 10 decision logic.
The measured local processing path is dominated by JSON parse, then local book update, then queue, then processor.
```

## V10 Disruptor Framework Compare Result

Command:

```bash
./scripts/binance-depth-compare.sh 500 BTCUSDT,ETHUSDT,BNBUSDT,SOLUSDT,XRPUSDT 4
```

Output:

```text
stored actual Binance.US depth compare messages=500 file=data/binance-depth-compare-2026-07-19T030213.441146197Z.jsonl
v5-depth-spsc processed=500 exchToRecvAvg=-8848.00us exchToRecvP99=35000.00us exchToDoneAvg=-8694.44us exchToDoneP99=35088.09us parseAvg=104.83us parseP99=320.95us bookAvg=21.80us bookP99=99.75us queueAvg=18.86us queueP99=149.09us processorAvg=3.84us processorP99=25.72us localE2EAvg=153.56us localE2EP99=516.16us producerOffer=2.63ms signals[B=21 S=23 N=43 X=413]
v10-depth-disruptor processed=500 exchToRecvAvg=-8848.00us exchToRecvP99=35000.00us exchToDoneAvg=-8688.15us exchToDoneP99=35087.18us parseAvg=104.83us parseP99=320.95us bookAvg=21.80us bookP99=99.75us queueAvg=19.34us queueP99=149.41us processorAvg=3.23us processorP99=15.99us localE2EAvg=159.85us localE2EP99=502.76us producerOffer=2.87ms signals[B=21 S=23 N=43 X=413]
```

Current reading:

```text
Disruptor improved processor p99 in this run: 25.72us -> 15.99us.
Disruptor did not improve queue average: 18.86us -> 19.34us.
Disruptor slightly improved localE2E p99: 516.16us -> 502.76us.
Disruptor had slightly worse localE2E average: 153.56us -> 159.85us.
For this workload, Disruptor is not categorically faster than JCTools SPSC. The next fair test is larger volume and repeated runs.
```

## V11 Raw Depth Disruptor Result

Command:

```bash
./scripts/binance-depth-raw-disruptor.sh 500 BTCUSDT,ETHUSDT,BNBUSDT,SOLUSDT,XRPUSDT 4
```

Output:

```text
stored raw Binance.US depth messages=500 file=data/binance-raw-disruptor-2026-07-19T032152.194632063Z.jsonl
v11-raw-disruptor processed=500 dropped=0 exchToRecvAvg=-10394.00us exchToRecvP99=-8000.00us exchToDoneAvg=-10164.12us exchToDoneP99=-7549.12us queueAvg=27.97us queueP99=172.14us parseAvg=117.45us parseP99=447.44us bookAvg=34.03us bookP99=273.57us processorAvg=6.47us processorP99=41.31us localE2EAvg=229.88us localE2EP99=695.98us producerOffer=5.81ms signals[B=36 S=24 N=148 X=292]
```

Current reading:

```text
V11 proves that Binance raw data can be published directly into Disruptor and processed by a handler chain.
This run did not outperform the simpler V9/V10 localE2E results.
The extra handler stages and more active threads are visible in queue, book, and localE2E costs.
This is expected when each event has small work and the public feed rate is moderate.
Disruptor is more likely to show value under burst replay, heavier feature calculation, or fan-out/fan-in stages.
```

## V12 Custom WebSocket Adapter Validation Result

Command:

```bash
./scripts/custom-ws-vs-baseline.sh
```

Output summary from 2026-07-19T03:56:06Z:

```text
Binance.US BTCUSDT: WS vs REST bidDiff=0 askDiff=0; WS vs XChange bidDiff=0 askDiff=0
Binance.US ETHUSDT: WS vs REST bidDiff=0.07 askDiff=0; WS vs XChange bidDiff=0.07 askDiff=0
OKX BTC-USDT: WS vs REST bidDiff=0 askDiff=0
OKX ETH-USDT: WS vs REST bidDiff=0 askDiff=0
Kraken BTC/USD: WS vs REST bidDiff=0 askDiff=0; WS vs XChange bidDiff=0 askDiff=0
Kraken ETH/USD: WS vs REST bidDiff=0 askDiff=0; WS vs XChange bidDiff=0 askDiff=0
```

Current reading:

```text
The custom WebSocket adapters produce top-of-book prices that match nearby REST snapshots and XChange where available.
This validates the field mapping for Binance.US, OKX, and Kraken at the best bid/ask level.
The next step is to promote these top-of-book adapters into full order-book update adapters that emit CanonicalOrderBookUpdate.
```

## V16 Data Source Engine Runtime Comparison

Purpose:

```text
Compare the previous connector-wrapper version against the refactored data-engine runtime path.
This is a live public-market smoke benchmark, not a strict latency benchmark, because exchange/network timing changes between runs.
```

Versions compared:

```text
V14-connector-wrapper:
  commit f584843 Add data source module design
  path: WebSocket adapter -> TopOfBookSnapshot -> REST/XChange comparison

V16-data-engine-runtime:
  working tree after version marker update
  path: MarketDataConnector.subscribe -> FanoutMarketDataSink -> MarketDataEngine
        -> MarketDataCache + MarketDataEventBus + RecordingMarketDataSink
        -> REST/XChange comparison reads from cache
```

Commands:

```bash
./scripts/custom-ws-vs-baseline.sh | tee data/datasource-v16-current-latest.log

git worktree add --detach /tmp/hft_java_prev_f584843 f584843
cd /tmp/hft_java_prev_f584843
./scripts/custom-ws-vs-baseline.sh | tee /home/yimo/hft_java/data/datasource-v14-prev-f584843-latest.log

cd /home/yimo/hft_java
scripts/compare-datasource-logs.py \
  data/datasource-v14-prev-f584843-latest.log \
  data/datasource-v16-current-latest.log \
  | tee data/datasource-version-comparison-latest.csv
```

Result:

```text
version,file,wsCount,avgWsLoadMs,avgEngineEtlUs,restSuccess,restExact,restAvgBidBps,restAvgAskBps,xchangeSuccess,xchangeExact,xchangeAvgBidBps,xchangeAvgAskBps,cacheTopOfBook,publishedEvents,replayRecords
V14-connector-wrapper,data/datasource-v14-prev-f584843-latest.log,6,1329.626667,0.000000,6,5,0.008613,0.017224,4,3,0.012919,0.025836,-,-,-
V16-data-engine-runtime,data/datasource-v16-current-latest.log,6,2108.258333,433.300000,6,6,0.000000,0.000000,4,4,0.000000,0.000000,6,6,6
```

Interpretation:

```text
V16 proves the new architecture is active: 6 cached top-of-book entries, 6 published events, and 6 replay records.
The V16 engine/cache/event-bus/recorder ETL overhead averaged 433.30us for this run.
The avgWsLoadMs comparison is not a clean architecture latency comparison because it includes live network/exchange timing.
Quality improved in this sample: V16 had exact price matches for 6/6 REST checks and 4/4 XChange checks.
The next stricter benchmark should use replay so both versions process identical captured input.
```
## V22 P0 Source-Partition Benchmark

Command:

```bash
./scripts/deep-book-latency-benchmark.sh \
  data/multi-exchange-raw-v22-2026-07-23T133841929141429Z.jsonl \
  4 500000 3
```

Median-throughput run comparison:

| Mode | Workers | Throughput | Processing p99 | Queue p99 | End-to-end p99 |
|---|---:|---:|---:|---:|---:|
| Direct | 1 | 68,466/s | 65.36 us | 0 us | 65.36 us |
| Source-partitioned | 4 | 136,199/s | 56.55 us | 22,701.97 us | 22,720.66 us |

The four-worker design produced `1.989x` throughput with final-book parity. Its saturated-burst end-to-end p99 was `347.618x` the direct path because queue wait dominated. This confirms that concurrency is useful for capacity, while queue occupancy is the controlling variable for latency.

Result artifact: `data/deep-book-latency-v22-2026-07-23T134103431852630Z.json`.

## V23 Default Runtime Decision

V23 uses direct single-writer processing as the default live architecture. A fresh six-source capture was repeated until each measured run contained `501,036` identical real records.

| Mode | Workers | Median throughput | Processing p99 | Queue p99 | End-to-end p99 |
|---|---:|---:|---:|---:|---:|
| Direct | 1 | 70,028/s | 63.42 us | 0 us | 63.42 us |
| Source-partitioned | 4 | 150,140/s | 56.83 us | 19,786.19 us | 19,801.80 us |

The source-partitioned experiment improved saturated throughput by `2.144x`, but queue wait raised end-to-end p99 by `317.270x`. At the observed live rate of roughly `580 events/s`, direct replay capacity had about `121x` headroom. Final-book parity was true.

Result artifact: `data/deep-book-latency-v23-2026-07-23T140302880339268Z.json`.

Future comparisons must replay identical captured records, report final-book parity, and separate processing, queue, and end-to-end latency.

## V24 Full-Pipeline And JMH Baseline

Two one-run regression validations exercised the production-shaped path. These are local baselines, not hardware claims.

| Input | Records | Throughput | Corrected E2E p99 | p99.9 | Allocation | GC | Drops | Parity |
|---|---:|---:|---:|---:|---:|---:|---:|---|
| V23 retained capture | 5,826 | 32,781/s | 1,215.38 us | 2,240.06 us | 36,461.4 B/msg | 2 / 3 ms | 0 | true |
| Fresh V24 journal | 1,932 | 26,306/s | 188.06 us | 2,561.33 us | 38,857.4 B/msg | 1 / 1 ms | 0 | true |

The full path includes recorder offer, protocol, parse, mutation, quality, snapshot, cache, core listeners, and async side-output offer. Different input mixes explain much of the percentile difference; this is not a version speedup comparison.

Default forked JMH used JDK 17.0.19, one thread, one fork, three 1-second warmups, five 1-second measurements, throughput and sample-time modes, and the GC profiler:

| Operation | Throughput (ops/us) | p50 us | p99 us | p99.9 us | bytes/op |
|---|---:|---:|---:|---:|---:|
| venue classification | 0.828 | 0.940 | 4.242 | 69.651 | 3,840 |
| JSON parse | 0.814 | 1.592 | 6.793 | 78.596 | 3,817 |
| book mutation | 15.037 | 0.073 | 0.307 | 8.450 | 416 |
| snapshot creation | 5.990 | 0.200 | 0.667 | 24.947 | 1,264 |
| LocalBookPublisher | 2.144 | 0.372 | 1.340 | 29.620 | 1,472 |
| cache + event publication | 3.093 | 0.220 | 0.599 | 21.917 | 128 |

The evidence prioritizes reducing repeated JSON tree parsing and full snapshot/object allocation. Replacing `BigDecimal` is not justified yet because exactness is part of correctness and isolated mutation is already much cheaper than parse/publication. The default remains `DIRECT_SINGLE_WRITER`.
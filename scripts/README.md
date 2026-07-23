# Scripts

These scripts are stable command-line entry points for the Java apps under `com.example.hft.app`.

```text
run.sh                         basic demo
test.sh                        self-tests
benchmark.sh                   synthetic benchmark
deep-book-latency-benchmark.sh real raw replay, direct vs partitioned
custom-ws-vs-baseline.sh       current Binance.US + OKX + Kraken WebSocket validation
custom-vs-xchange.sh           REST adapter vs XChange snapshot validation
xchange-rest.sh                XChange REST experiment
binance.sh                     Binance replay demo
binance-latency.sh             Binance live local latency demo
binance-actual-latency.sh      Binance ticker actual-data latency
binance-depth-latency.sh       Binance depth order-book latency
binance-depth-compare.sh       JCTools SPSC vs Disruptor comparison
binance-depth-raw-disruptor.sh raw depth -> Disruptor handler chain
multi-exchange-local-books.sh  V23 direct single-writer six-source local books
```
## Data Source Comparison

Summarize saved validation logs:

```bash
scripts/compare-datasource-logs.py data/datasource-v14-prev-f584843-latest.log data/datasource-v16-current-latest.log
```

The parser reports WebSocket load time, engine ETL overhead, REST/XChange quality, and cache/event/replay counts.
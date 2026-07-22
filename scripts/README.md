# Scripts

These scripts are stable command-line entry points for the Java apps under `com.example.hft.app`.

```text
run.sh                         basic demo
test.sh                        self-tests
benchmark.sh                   synthetic benchmark
custom-ws-vs-baseline.sh       current Binance.US + OKX + Kraken WebSocket validation
custom-vs-xchange.sh           REST adapter vs XChange snapshot validation
xchange-rest.sh                XChange REST experiment
binance.sh                     Binance replay demo
binance-latency.sh             Binance live local latency demo
binance-actual-latency.sh      Binance ticker actual-data latency
binance-depth-latency.sh       Binance depth order-book latency
binance-depth-compare.sh       JCTools SPSC vs Disruptor comparison
binance-depth-raw-disruptor.sh raw depth -> Disruptor handler chain
```
# Scripts

Stable command-line entry points for `com.example.hft.app`.

```text
run.sh                         basic demo
test.sh                        deterministic self-tests
benchmark.sh                   synthetic legacy benchmark
multi-exchange-local-books.sh  V24 direct six-source books + journal + parity
deep-book-latency-benchmark.sh identical-record direct vs partitioned capacity
full-pipeline-benchmark.sh      complete production-shaped replay benchmark
jmh-deep-book.sh                forked JMH stage microbenchmarks
custom-ws-vs-baseline.sh       WebSocket vs REST/XChange validation
deep-book-sources.sh           public deep-book source validation
```

`deep-book-latency-benchmark.sh` is intentionally narrower than the full pipeline. `full-pipeline-benchmark.sh` includes recorder offer, protocol, parse, mutation, quality, snapshot, engine/cache, deterministic listeners, async offer, allocation, GC, drops, and parity.

The live command keeps `DIRECT_SINGLE_WRITER`. The final duration argument is a smoke/capture window, not a production connection-lifetime requirement.
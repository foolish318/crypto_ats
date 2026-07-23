# Strategy And Benchmark Module

![Strategy and benchmark module](strategy-benchmark.svg)

PNG fallback: [strategy-benchmark.png](strategy-benchmark.png)

Strategy consumes immutable accepted snapshots. Benchmarking measures the cost of each local stage rather than reporting only one total.

## Decision Examples

```text
spread validity
top-5 and top-10 depth imbalance
cross-venue best bid and ask
freshness and quality eligibility
```

The current `TradingSignal` is a decision result, not an exchange order. Order submission and risk remain a future separate lifecycle.

## Latency Distributions

```text
exchange -> local receive
parse
quality and book update
queue handoff
processor
local receive -> decision complete
exchange event -> decision complete
```

Average, median, p95, p99, maximum, throughput, and rejection counts describe different behavior. Replay is required for fair Java implementation comparisons.

## Current Code

```text
src/main/java/com/example/hft/strategy/
src/main/java/com/example/hft/pipeline/
src/main/java/com/example/hft/benchmark/
```

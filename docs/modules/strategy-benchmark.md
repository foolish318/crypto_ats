# Strategy And Benchmarks

![Strategy and benchmarks](strategy-benchmark.svg)

`DeepBookStrategyListener` consumes only accepted, current, fresh books. `CrossExchangeBookView` supplies immutable canonical NBBO snapshots without exposing mutable maps.

`DeepBookJmhBenchmark` measures classification, JSON parse, mutation, snapshot, publisher, and cache/event-bus stages. `FullPipelineReplayBenchmark` exercises the same major path as live processing and reports throughput, latency percentiles through p99.9, max, allocation, GC, queue/listener lag, drops, rejects, and replay parity.
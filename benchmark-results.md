# Current Performance Baseline

These results describe release `1.1.0` on Java 17.0.19. They are a reproducibility baseline, not an exchange-network latency claim.

## Full Pipeline Replay

The measured path is ingress -> recorder offer -> parse -> protocol -> book mutation -> quality gate -> snapshot -> engine -> cache -> event bus -> StrategyMarketDataPort and side consumers. Three runs followed one warmup; the table reports the median-throughput run.

| Records | Throughput | Corrected p99 | Corrected p99.9 | Allocation/message | GC | Drops | Parity |
|---:|---:|---:|---:|---:|---:|---:|:---:|
| 746 | 16,689 msg/s | 305.80 us | 2,175.09 us | 43,410.3 B | 1 / 1 ms | 0 | true |

Snapshot bootstrap and incremental update latencies are emitted separately in the machine-readable benchmark JSON. Stage distributions include parse, protocol, book, publish, and listener work. The corrected latency histogram accounts for coordinated omission.

## JMH Microbenchmarks

Configuration: 3 warmups, 5 measurements, 1 fork, GC profiler.

| Stage | Throughput (ops/us) | p50 (us) | p99 (us) | p99.9 (us) | Bytes/op |
|---|---:|---:|---:|---:|---:|
| classification | 0.864 | 1.428 | 4.688 | 54.518 | 3,840 |
| JSON parse | 0.879 | 1.528 | 6.683 | 74.905 | 3,817 |
| book mutation | 13.617 | 0.093 | 0.293 | 11.727 | 416 |
| snapshot creation | 7.604 | 0.230 | 1.115 | 23.717 | 1,232 |
| publisher + strategy port | 0.922 | 0.795 | 3.772 | 63.722 | 2,840 |
| cache + event bus + strategy port | 1.123 | 0.608 | 2.056 | 53.248 | 1,576 |

The StrategyMarketDataPort is included in both publication measurements. Its p99 remains low relative to parsing and book reconstruction, while canonical immutable publication raises allocation. The evidence still supports `DIRECT_SINGLE_WRITER`; parser allocation, BigDecimal churn, and top-N snapshot copying are the next measured Java-level optimization targets. The 746-record live capture is a correctness and regression baseline, not a saturation or production-capacity result.
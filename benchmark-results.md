# Current Performance Baseline

These results describe release `1.0.0` on Java 17.0.19. They are a reproducibility baseline, not an exchange-network latency claim.

## Full Pipeline Replay

The measured path is ingress -> recorder offer -> parse -> protocol -> book mutation -> quality gate -> snapshot -> engine -> cache -> event bus -> consumers. One warmup preceded the measured run.

| Records | Throughput | Corrected p99 | Corrected p99.9 | Allocation/message | GC | Drops | Parity |
|---:|---:|---:|---:|---:|---:|---:|:---:|
| 2,376 | 23,089 msg/s | 1,398.51 us | 2,169.22 us | 37,957.4 B | 2 / 4 ms | 0 | true |

Snapshot bootstrap and incremental update latencies are emitted separately in the machine-readable benchmark JSON. Stage distributions include parse, protocol, book, publish, and listener work. The corrected latency histogram accounts for coordinated omission.

## JMH Microbenchmarks

Configuration: 3 warmups, 5 measurements, 1 fork, GC profiler.

| Stage | Throughput (ops/us) | p50 (us) | p99 (us) | p99.9 (us) | Bytes/op |
|---|---:|---:|---:|---:|---:|
| classification | 0.828 | 0.940 | 4.242 | 69.651 | 3,840 |
| JSON parse | 0.814 | 1.592 | 6.793 | 78.596 | 3,817 |
| book mutation | 15.037 | 0.073 | 0.307 | 8.450 | 416 |
| snapshot creation | 5.990 | 0.200 | 0.667 | 24.947 | 1,264 |
| publisher | 2.144 | 0.372 | 1.340 | 29.620 | 1,472 |
| cache + event bus | 3.093 | 0.220 | 0.599 | 21.917 | 128 |

The evidence supports keeping `DIRECT_SINGLE_WRITER` as the live default. Parsing and full snapshot allocation are the main Java-level optimization candidates; changes should be accepted only when parity tests remain green and end-to-end tail latency improves.
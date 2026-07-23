# Framework Decisions

The current release deliberately uses Java core concurrency plus Jackson. Framework selection is evidence driven; a familiar brand name is not itself a latency improvement.

| Technology | Useful when | Current decision |
|---|---|---|
| LMAX Disruptor | high-rate in-process fan-out with stable preallocated events | Not used. Current source rates do not justify an extra queue on the book hot path. |
| JCTools | specialized bounded SPSC/MPSC queues | Not used in the hot path. `ArrayBlockingQueue` is confined to measured side outputs and journal recording. |
| Aeron | low-latency IPC/UDP between processes or hosts | Not used. The current deployment is one JVM and has no measured IPC requirement. |
| Kafka | durable distributed integration and downstream replay | Not used in realtime book mutation. It may later serve noncritical analytics or enterprise distribution. |
| Chronicle Queue | memory-mapped durable local journal | Not used. The current journal contract first makes rotation, checksums, fsync, retention, and replay safety explicit. |
| SBE | fixed-schema binary messages between controlled endpoints | Not used on public JSON WebSocket feeds. It becomes relevant at an internal binary boundary or supported venue feed. |
| XChange / CCXT | broad exchange coverage behind common APIs | Not used for the critical depth path because venue sequence, checksum, and recovery rules remain venue specific. |

## Current Concurrency Decision

Each source book has one sequential writer. This preserves venue ordering and removes queue wait from message-to-book latency. Deterministic consumers such as cache, consolidated view, and strategy run inline. Recorder, analytics, and external I/O belong on bounded asynchronous side channels with observable depth, lag, drops, and errors.

A multi-JVM or distributed design is warranted for failure-domain isolation, independent scaling, or process ownership, not automatically for lower latency. Aeron or another IPC layer should be introduced only after a measured boundary requires it and the added serialization/transport tail latency is acceptable.

## Revisit Criteria

Reconsider a framework only when a reproducible benchmark demonstrates one of these conditions:

- sustained ingress approaches the capacity of the direct path
- inline deterministic consumers dominate p99/p99.9
- a required process boundary needs reliable low-latency IPC
- the local journal cannot meet durability, recovery-time, or retention requirements
- a controlled binary schema replaces public JSON at a meaningful boundary

Any experiment must preserve bounded pressure, generation fencing, availability invalidation, and deterministic replay parity.
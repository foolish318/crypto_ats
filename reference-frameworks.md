# Reference Frameworks And Architecture Decisions

This document records what major trading and messaging frameworks are designed to solve, where they fit in this project, and why the current live hot path remains direct and single-writer.

## Current Decision

```text
Default live mode: DIRECT_SINGLE_WRITER
Distributed hot path: no
Partitioned workers: benchmark/capacity experiment only
Recorder and replay: asynchronous side path
```

The latest measured public-feed rate was roughly 580 messages/second. Identical-record V23 replay sustained a median 70,028 messages/second on the direct path, leaving roughly 121x processing headroom. Four partition workers increased median throughput to 150,140/second, but saturated end-to-end p99 increased from 63.42 microseconds to 19.80 milliseconds because queue wait dominated.

The project therefore optimizes the current hot path for latency and deterministic ownership, while retaining partitioned and distributed options for later capacity, isolation, and availability requirements.

## Terminology

| Model | Boundary | Primary benefit | Typical latency cost |
|---|---|---|---|
| Direct single-writer | Method call / source callback | Lowest local latency and deterministic state | No extra handoff queue |
| Multithreaded partitions | Threads in one JVM | Aggregate throughput across independent symbols | Scheduling and queue wait |
| Multi-process | JVMs on one host | GC, crash, and deployment isolation | Serialization plus IPC |
| Distributed | Processes on multiple hosts | Horizontal scale, HA, and geographic placement | Network, serialization, clocks, partial failure |

Distribution does not make one sequential order-book update faster. It can improve system throughput or tail latency only when the removed contention/backlog costs more than the new communication hop.

## Framework Comparison

| Framework | What it is for | Appropriate project use | Current decision |
|---|---|---|---|
| NautilusTrader | Deterministic event-driven trading engine with a single-threaded core and in-process message bus | Reference for cache/engine/bus ownership, immutable events, and process isolation boundaries | Follow its deterministic core pattern; no external bus in the live hot path |
| LMAX Disruptor | Preallocated bounded ring buffer and dependency graph with single-writer ownership | High-throughput fan-out or staged processing when measured load justifies a handoff | Keep as benchmark/optional capacity mode, not default live intake |
| JCTools | Low-level lock-free Java queue implementations | SPSC/MPSC experiments after producer/consumer cardinality is known | Queue primitive only; it does not decide the architecture |
| Aeron Transport | Brokerless IPC on one host and reliable UDP across hosts | Future multi-JVM market-data/strategy boundary when GC or crash isolation is required | Do not add until an IPC boundary has a measured operational need |
| Aeron Archive | Recording and replay of Aeron streams | Future binary replay/archive if JSONL becomes a bottleneck | Current `AsyncRawRecorder` and deterministic replay are sufficient |
| Aeron Cluster | Raft-based replicated log and fault-tolerant services | HA sequencer, replicated order/risk state, or hot standby | Availability feature, not a single-event latency optimization |
| Chronicle Queue | Memory-mapped persisted low-latency IPC and replay | Same-host durable service boundary | Candidate if persistence plus IPC must share one mechanism |
| Apache Kafka | Distributed durable log, partitions, consumer groups, batching | Analytics, audit, research, downstream ETL, and non-hot-path distribution | Keep outside market-data-to-decision hot path |
| Redis Streams | Moderate-scale retained streams with consumer groups and replay | Control-plane events, dashboards, jobs, and short-retention integration | Keep outside order-book mutation and strategy decision path |
| SBE | Schema-generated compact binary encoding | Future Aeron/IPC/network wire format | Useful only after a binary process boundary exists |
| XChange | Unified Java exchange API facade | REST comparison, discovery, and broad adapter coverage | Keep for validation/convenience; custom WebSocket adapters remain the latency path |

## Evidence From Reference Projects

### NautilusTrader

NautilusTrader documents a single-threaded core for deterministic event ordering. Its production guidance is to place multiple strategies in one node and use separate processes for parallel execution or workload isolation. Its external message egress is explicitly offloaded so external I/O does not block the trading bus.

- [Architecture](https://nautilustrader.io/docs/nightly/concepts/architecture/)
- [Message Bus](https://nautilustrader.io/docs/latest/concepts/message_bus/)
- [Design Principles](https://nautilustrader.io/docs/latest/developer_guide/design_principles/)

### LMAX Disruptor

The Disruptor design emphasizes that mutable data should have one writing thread, eliminating write contention. Its bounded ring buffer is valuable when a real producer/consumer dependency graph needs high throughput; adding it without load merely adds a handoff.

- [LMAX Disruptor technical paper](https://lmax-exchange.github.io/disruptor/files/Disruptor-1.0.pdf)
- [Disruptor project](https://lmax-exchange.github.io/disruptor/)

### Aeron

Aeron separates transport, archive, and cluster responsibilities. Transport supports IPC and UDP with predictable latency. Archive adds persistent replay. Cluster adds Raft-based fault tolerance and total ordering. A cluster is therefore selected for availability and replicated state, not because a network hop beats an in-process method call.

- [Aeron documentation](https://aeron.io/docs/)
- [Aeron transport overview](https://aeron.io/docs/aeron/overview/)
- [Aeron Cluster overview](https://aeron.io/docs/aeron-cluster/overview/)

### Kafka And Redis

Kafka intentionally trades configurable buffering latency for batching, throughput, persistence, and distributed consumption. Redis Streams provides retention, acknowledgements, consumer groups, and replay. Those are valuable integration features, but neither belongs between a market-data callback and a local-book update in this project.

- [Kafka design](https://kafka.apache.org/28/design/design/)
- [Redis Streams](https://redis.io/docs/latest/develop/use-cases/streaming/)

### Chronicle Queue

Chronicle Queue combines persisted replay with low-latency IPC through memory-mapped files. It becomes relevant if the project deliberately splits into same-host services and requires the boundary itself to be durable.

- [Chronicle Queue](https://chronicle.build/queue/)

## Current Hot Path

```text
Exchange REST/WebSocket
  -> ingress RawEnvelope recording
  -> inline venue protocol classification
  -> source-owned LocalOrderBookBuilder
  -> quality/state/freshness gate
  -> AcceptedLocalBookEvent
  -> MarketDataEngine
      -> cache
      -> in-process event bus
      -> strategy
```

"Single-writer" does not mean the entire JVM has only one operating-system thread. HTTP/WebSocket clients, recorder, scheduler, and different connections may use other threads. It means one mutable `exchange + symbol` book is updated sequentially without an application-level worker queue between receive and apply.

## Side Paths

The following work should remain asynchronous and must not block the decision path:

```text
raw persistence
replay
metrics and dashboards
research and analytics
Kafka/Redis export
remote monitoring
historical storage
```

## When To Reconsider Multithreading

Enable source partitions only after repeated measurement shows at least one of these conditions:

```text
sustained direct-path CPU saturation
queue-free direct throughput below required peak rate
processing p99/p99.9 misses its budget because independent books contend
many more symbols or heavier strategy calculations
one source callback blocks unrelated sources
```

The acceptance test must compare identical records and require both final-book parity and an improved end-to-end latency/throughput objective. Higher throughput alone is not enough when the live rate is already far below direct capacity.

## When To Reconsider Distribution

Introduce multi-JVM or multi-host boundaries for an explicit non-local requirement:

```text
GC or crash isolation
independent deployments
active/passive or active/active high availability
recovery-time and recovery-point objectives
geographic proximity to different venues
symbol count beyond one host's measured capacity
regulatory or operational separation of risk/order services
```

Start with same-host Aeron IPC and benchmark the complete receive-to-decision path. Add cross-host transport or consensus only when HA or horizontal scaling justifies the measured latency cost.
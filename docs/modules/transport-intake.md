# Transport And Raw Intake Module

![Transport and raw intake](transport-intake.svg)

PNG fallback: [transport-intake.png](transport-intake.png)

V24 extracts `VenueTransport` and `SnapshotProvider` so WebSocket and HTTP behavior can be replaced with deterministic fakes. `JdkVenueTransport` and `JdkSnapshotProvider` keep the current live behavior.

Protocol control is handled through `VenueProtocolStateMachine`; subscription ACK, heartbeat, ping/pong timeout, fragmented message assembly, and book mutation are separate responsibilities. `BookRecoveryPolicy` owns recovery scheduling/backoff, while `BookPipeline` owns ordered builder and publication calls.

The source callback first offers a complete `RawEnvelope` to the bounded recorder, then processes the same envelope through the direct source-owned path. No database, network export, or unbounded task is allowed to block book mutation.
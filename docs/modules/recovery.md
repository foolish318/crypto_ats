# Recovery

![Recovery](recovery.svg)

`SessionHealth` separates transport, book, and session state. `StaleWatchdog` marks a source stale when no message arrives before the threshold. `RecoveryCoordinator` serializes reconnect state, uses exponential backoff with jitter from 300 ms to 30 seconds, and records attempts, successes, failures, duration, and reason.

Leaving live state publishes availability invalidation immediately. A new generation becomes live only after snapshot/bridge, continuity, and quality checks succeed. `stop()` fences callbacks and prevents further recovery scheduling.
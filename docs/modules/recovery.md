# Recovery Coordinator Module

![Recovery coordinator module](recovery.svg)

PNG fallback: [recovery.png](recovery.png)

Recovery is a state rebuild, not just a socket reconnect.

## Transition

```text
failure detected
  -> mark affected book DEGRADED
  -> suppress its publication
  -> reconnect with a bounded retry policy when needed
  -> load a fresh snapshot
  -> bridge buffered updates
  -> rerun quality validation
  -> return to LIVE
```

Metrics distinguish reconnect attempts, snapshot reloads, successful rebuilds, failures, and recovery duration. Other venue books continue independently.

## Current Scope

V18 implements reconnect and automatic resnapshot for the Binance.US raw-depth path. The same coordinator contract should own OKX and Kraken recovery as their continuous builders are added.

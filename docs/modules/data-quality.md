# Data Quality Module

This module was introduced in V20. It sits between parsing and publication of a local order book.

![Data quality module](data-quality.svg)

PNG fallback:

```text
docs/modules/data-quality.png
```

## Contract

Input:

```text
parsed venue snapshot or incremental update
current venue-local book state
exchange and receive timestamps
sequence or checksum metadata
```

Output:

```text
ACCEPT
  -> update may be published downstream

REJECT
  -> book is degraded
  -> update is not published
  -> recovery is requested
```

Transport status and data quality are separate. A connected WebSocket can still carry stale, incomplete, crossed, or checksum-invalid data.

## Common Checks

```text
required fields and correct symbol
positive price and valid quantity
bids descending and asks ascending in snapshots
best bid below best ask
event timestamp within the freshness threshold
```

## Venue Checks

| Venue | Validation |
|---|---|
| Binance.US | Buffer WS first, bridge REST `lastUpdateId` with `U/u`, then require continuity |
| OKX | Require `prevSeqId` to match the preceding `seqId` |
| Kraken | Apply updates and verify CRC32 over top 10 asks followed by top 10 bids |

## Current Implementation

```text
src/main/java/com/example/hft/datasource/deepbook/quality/
  DeepBookQualityValidator.java
  DeepBookQualityReport.java
  KrakenBookChecksum.java
  DeepBookQualityValidatorSelfTest.java
```

The deterministic tests inject Binance and OKX sequence gaps and a Kraken checksum mismatch. The latest live probe accepted all six configured sources.

## Scope

V20 proves and packages the validation rules. Continuous validation belongs inside each long-running venue order-book builder. That is an implementation expansion of this module, not a new pipeline.

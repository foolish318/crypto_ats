# Data Quality Gate Module

![Data quality gate](data-quality.svg)

PNG fallback: [data-quality.png](data-quality.png)

## Contract

Input is a canonical snapshot or update plus the current venue-local state, exchange and receive timestamps, and sequence/checksum metadata.

```text
ACCEPT -> event may mutate the publishable local book
REJECT -> mark the book DEGRADED, suppress publication, request recovery
```

Transport health and data quality are independent. A connected WebSocket can still carry stale, incomplete, crossed, discontinuous, or checksum-invalid data.

## Checks

| Scope | Validation |
|---|---|
| Common | Required fields, positive price, valid quantity, sorted snapshot sides, freshness, and non-crossed top of book |
| Binance.US | Bridge snapshot `lastUpdateId` with `U/u`, then require update continuity |
| OKX | Require `prevSeqId` to match the preceding `seqId` |
| Kraken | Verify CRC32 across the exchange-defined top ten ask and bid levels |

## Current Code

```text
src/main/java/com/example/hft/datasource/deepbook/quality/
  DeepBookQualityValidator.java
  DeepBookQualityReport.java
  KrakenBookChecksum.java
  DeepBookQualityValidatorSelfTest.java
```

The deterministic tests inject Binance and OKX sequence gaps and a Kraken checksum mismatch.

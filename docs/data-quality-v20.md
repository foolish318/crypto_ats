# V20 Deep-Book Data Quality Gate

V20 changes source discovery from "a payload arrived" to "the payloads are structurally and logically safe to use." The live probe captures a snapshot plus an incremental message, or buffered diff messages around a Binance.US snapshot, and passes them through venue-specific validation.

![V20 data pipeline](data-source-architecture-v20.svg)

The detailed gate:

![V20 quality gate](data-quality-gate-v20.svg)

Raster copies are also available for viewers that do not render SVG:

```text
docs/data-source-architecture-v20.png
docs/data-quality-gate-v20.png
```

## Runtime Boundary

```text
exchange REST/WebSocket
  -> raw JSON payloads
  -> exact decimal parsing
  -> common order-book checks
  -> venue-specific sequence/checksum checks
  -> ACCEPT: publish a quality-approved book
  -> REJECT: suppress the book and request reconnect/resync
```

The current `DeepBookSourceDiscoveryMain` is a finite live probe. It checks at least two consecutive updates so continuity can be tested instead of checking only one snapshot. A production connector will run the same gate continuously and trigger reconnect/resync after any failure.

## Correct Binance Bootstrap

The probe originally opened the WebSocket after downloading the REST snapshot. The first live V20 run correctly rejected both Binance books because updates occurring between those two operations were missing.

The corrected flow follows the exchange's local-book rules:

```text
1. Open WebSocket diff-depth stream.
2. Buffer updates without applying them.
3. Download REST depth snapshot.
4. Drop buffered updates with u < snapshot lastUpdateId + 1.
5. Find the first update where U <= snapshot + 1 <= u.
6. Require every later update U to equal previous u + 1.
7. Apply only the bridged, contiguous updates.
```

This incident is why transport status and book quality must be separate: the connection was healthy, but the first local bootstrap was not complete.

## Quality Checks

| Gate | Binance.US | OKX | Kraken |
|---|---|---|---|
| Schema and symbol | Required REST/WS fields and exact symbol | `channel`, `instId`, action, data | channel, type, symbol, data |
| Numeric validity | Price > 0; snapshot qty > 0; delta qty >= 0 | Same | Same |
| Snapshot ordering | Bids descending; asks ascending | Bids descending; asks ascending | Bids descending; asks ascending |
| Book integrity | Best bid < best ask after every update | Best bid < best ask after every update | Best bid < best ask after every update |
| Continuity | First `U/u` bridges snapshot; next `U = previous u + 1` | `prevSeqId = previous seqId`; `seqId >= prevSeqId` | WebSocket order plus non-decreasing event timestamp |
| Freshness | Event `E` within 30 seconds | Book `ts` within 30 seconds | RFC3339 timestamp within 30 seconds |
| Checksum | Not supplied by this feed | Deprecated for `books`; use sequence IDs | CRC32 over top 10 asks then top 10 bids |

For Kraken, decimal text is preserved while parsing because trailing zero precision is part of the official CRC32 input. The checksum implementation is verified against Kraken's published `3310070434` example.

## Failure Policy

```text
PASS all gates
  -> qualityAccepted=true
  -> source is usable
  -> downstream local-book publication is allowed

FAIL any gate
  -> qualityAccepted=false
  -> source is rejected
  -> strategy must not consume the book
  -> production connector should reconnect or reload a snapshot
```

Transport success and data quality are deliberately separate. A WebSocket can be connected while its data is stale, crossed, out of sequence, or checksum-invalid.

## Java Files

```text
src/main/java/com/example/hft/datasource/deepbook/quality/
  DeepBookQualityValidator.java
  DeepBookQualityReport.java
  KrakenBookChecksum.java
  DeepBookQualityValidatorSelfTest.java

src/main/java/com/example/hft/app/DeepBookSourceDiscoveryMain.java
  Captures multiple live messages and records quality fields.
```

## Verification

```bash
mvn -q compile
./scripts/test.sh
./scripts/deep-book-sources.sh data
```

Latest live result:

```text
version=V20-deep-book-quality-gate
sources=6
connected=6
qualityAccepted=6
rejected=0

Binance.US BTCUSDT  qualityPassed=7 qualityFailed=0
Binance.US ETHUSDT  qualityPassed=7 qualityFailed=0
OKX BTC-USDT        qualityPassed=6 qualityFailed=0
OKX ETH-USDT        qualityPassed=6 qualityFailed=0
Kraken BTC/USD      qualityPassed=7 qualityFailed=0
Kraken ETH/USD      qualityPassed=7 qualityFailed=0
```

The JSONL output is named `data/deep-book-quality-v20-<run-id>.jsonl`. Important fields are:

```text
transportSuccess
qualityAccepted
qualityCheckedMessages
qualityChecksPassed
qualityChecksFailed
qualityFailures
qualitySequence
qualityChecksum
```

## Deterministic Tests

The self-test suite covers both valid and corrupted data:

```text
Binance contiguous U/u accepted
Binance sequence gap rejected
OKX contiguous prevSeqId/seqId accepted
OKX prevSeqId gap rejected
Kraken valid CRC32 accepted
Kraken CRC32 mismatch rejected
Kraken official checksum example equals 3310070434
```

## Protocol References

- [Binance.US diff-depth and local-book sequencing](https://github.com/binance-us/binance-us-api-docs/blob/master/web-socket-streams.md)
- [OKX WebSocket books and sequence IDs](https://www.okx.com/docs-v5/en/)
- [Kraken WebSocket v2 book](https://docs.kraken.com/api/docs/websocket-v2/book/)
- [Kraken CRC32 book checksum guide](https://docs.kraken.com/api/docs/guides/spot-ws-book-v2/)

# Local Order-Book Module

The local order-book module maintains exchange state after parsing and before downstream strategy use.

![Local order-book module](order-book.svg)

PNG fallback:

```text
docs/modules/order-book.png
```

## Ownership Rule

Each `exchange + symbol` owns a separate book:

```text
Binance.US BTCUSDT book
OKX BTC-USDT book
Kraken BTC/USD book
```

These books are never merged at the raw level. A cross-exchange view compares them only after each book is independently valid.

## Lifecycle

```text
open live stream
  -> buffer updates
  -> obtain snapshot when required
  -> bridge snapshot and updates
  -> apply contiguous changes
  -> run the quality gate after each message
  -> publish LIVE book state
```

On sequence gap, checksum failure, stale feed, or crossed book:

```text
mark DEGRADED
  -> suppress publication
  -> reconnect or reload snapshot
  -> rebuild
  -> return to LIVE
```

## Implemented Milestones

| Version | Delivered |
|---|---|
| V17 | Binance raw depth capture, snapshot bridge, sequence checks, local book, replay |
| V18 | Binance reconnect and automatic snapshot resync |
| V19 | Binance.US, OKX, and Kraken deep-book source catalog |
| V20 | Reusable quality rules for all three venues |

The next step is to run the OKX and Kraken books continuously using the same module boundary already shown in the canonical architecture.

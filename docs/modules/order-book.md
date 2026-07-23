# Venue-Local Order-Book Module

![Venue-local order book](order-book.svg)

PNG fallback: [order-book.png](order-book.png)

## Ownership

Each `exchange + symbol` owns independent state:

```text
Binance.US + BTCUSDT
OKX        + BTC-USDT
Kraken     + BTC/USD
```

Raw books are never merged. The cross-exchange view compares only books that are independently validated and `LIVE`.

## Lifecycle

```text
open stream and buffer updates
  -> obtain snapshot
  -> discard stale buffered updates
  -> find the first update that bridges the snapshot
  -> apply contiguous changes through BookSequencer
  -> validate
  -> publish LIVE state
```

On a gap, checksum mismatch, stale feed, or crossed book, publication is suppressed and recovery owns the transition back to `LIVE`.

## Current Code

```text
src/main/java/com/example/hft/datasource/book/
  SequencedLocalOrderBook.java
  BookSequencer.java
  BookQuality.java
  DepthUpdateApplyResult.java
```

The continuous Binance.US path is implemented. OKX and Kraken deep-book sources and quality rules exist, while their continuous local-book builders remain the next expansion inside this same module.

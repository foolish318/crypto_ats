# Module Guide

This guide describes the single current implementation (`1.1.0`).

| Area | Package | Responsibility |
|---|---|---|
| Live runner | `com.example.hft.app` | Wires six books, six trade streams, both journals, replay checks, and summaries |
| Book sources | `datasource.deepbook` | Binance.US, OKX, and Kraken depth definitions |
| Book runtime | `datasource.deepbook.runtime` | Transport lifecycle, snapshot bridge, sequence/checksum policy, recovery, local books, raw journal/replay |
| Engine | `datasource.engine` | Generation-fenced cache, cache-before-publish ordering, isolated listeners |
| Reference data | `datasource.instrument` | Venue metadata, tick/lot validation, venue-symbol to canonical instrument mapping |
| Canonical model | `marketdata.model` | Venue, InstrumentId, event header, BookSnapshot/BookDelta, PublicTrade, health, side and depth values |
| Public trades | `marketdata.trade` | Venue sources, normalizers, deduplication, ordering metrics, reconnecting sessions |
| Strategy API | `marketdata.api` | Immutable OrderBookView, notifications, StrategyMarketDataPort, MultiVenueBookView, strategy-neutral BookMath |
| Normalized replay | `marketdata.recording` | Bounded canonical event recorder and streaming deterministic replay |

## Book Ownership

Each `LiveBookSession` owns one mutable venue/instrument book and is its only writer. Successful snapshot or delta application increments `bookVersion`; recovery clears book contents but does not move the process-local version backwards. Publication copies only configured top-N depth into immutable canonical state.

## Publication Contract

```text
validate event
-> mutate local book
-> increment bookVersion
-> create immutable BookSnapshot
-> update StrategyMarketDataPort state
-> emit BookUpdated(version=N)
```

A callback observing version N can immediately read version N. Availability changes are generation fenced and update the view health before status notification.

## Trade Ordering

Book and public-trade WebSockets are independent streams. Each stream has its own `streamEpoch` and monotonic local sequence. No artificial total order is claimed across book and trade channels. Duplicate trades are suppressed; detectable exchange-time reversals are published with an out-of-order metric.

## Consumer Read Safety

`OrderBookView`, `BookSnapshot`, book levels, and `MultiVenueBookView` are immutable. Consumers never receive `TreeMap`, `JsonNode`, WebSocket, or builder references. `BookMath` provides available quantity, sweep price, and executable VWAP without fee, signal, or execution assumptions.
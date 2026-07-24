# Strategy Port And Benchmarks

![Strategy port and benchmarks](strategy-benchmark.svg)

`StrategyMarketDataPort` is data-only. Push methods are `onBookUpdated`, `onTrade`, and `onBookStatusChanged`. Pull methods are `getBook`, `getBooks`, and `latestTrade`. No feature, signal, fair value, order, or risk type is part of the API.

The port supports future cross-venue observation through `MultiVenueBookView`, future market making through immutable L2 plus public trades, and future Alpha research through sequenced canonical state/trade logs and deterministic replay. JMH and full raw-replay benchmarks remain separate from strategy logic.
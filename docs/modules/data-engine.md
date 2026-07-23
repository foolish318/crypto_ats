# Data Engine

![Data engine](data-engine.svg)

`MarketDataEngine` is the single boundary for accepted books, health changes, and errors. It updates `MarketDataCache` before publishing events. Cache entries are fenced by generation and support tombstones/removal when a source becomes unavailable.

`MarketDataEventBus` isolates listener failures. Core deterministic listeners execute inline. Side outputs use independent bounded queues and expose current/max depth, lag, drops, errors, and last failure.
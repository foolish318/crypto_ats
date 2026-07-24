# Recorder And Replay

![Recorder and replay](recorder-replay.svg)

The raw journal records venue payloads, REST snapshots, stream epochs, connection lifecycle, and recovery for diagnosis and re-normalization.

`AsyncNormalizedEventRecorder` separately records the canonical books, trades, and book-status changes accepted by `StrategyMarketDataPort`. It uses a bounded 65,536-event queue and never blocks the market-data callback. Overflow is explicit through `normalizedReplaySafe=false`. `NormalizedEventReplay` streams the log and reconstructs the same bookVersion, health, top-N state, and latest trade.
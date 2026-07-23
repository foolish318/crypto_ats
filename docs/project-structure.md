# Project Structure

The repository remains one Maven module. Package boundaries are used before adding process or network boundaries.

```text
com.example.hft.app
  Runnable live, replay, and benchmark entry points.

com.example.hft.datasource.deepbook
  Binance.US, OKX, and Kraken depth-source definitions.

com.example.hft.datasource.deepbook.quality
  Venue/common validation and Kraken checksum.

com.example.hft.datasource.deepbook.runtime
  VenueTransport, SnapshotProvider, protocol state machine, LiveBookSession,
  BookPipeline, source-owned builders, health/recovery, availability events,
  consolidated books, segmented journal/replay, and full-pipeline benchmark.

com.example.hft.datasource.instrument
  Venue metadata and canonical instrument mapping.

com.example.hft.datasource.engine
  Generation-fenced cache, cache-first engine, inline and bounded async event bus.

com.example.hft.exchange.*
  Legacy/public REST and WebSocket adapter experiments.

com.example.hft.marketdata.model / strategy / pipeline / benchmark
  Earlier learning models, decision examples, queue experiments, and metrics.
```

## V24 Runtime Ownership

```text
VenueTransport + SnapshotProvider
  -> LiveBookSession (transport/lifecycle orchestration)
  -> VenueProtocolStateMachine (ACK/heartbeat/pong/control)
  -> BookPipeline (one sequential writer)
  -> LocalOrderBookBuilder (venue sequence/checksum mutation)
  -> LocalBookPublisher (quality/state/freshness gate)
  -> MarketDataEngine
      -> MarketDataCache (generation tombstone)
      -> MarketDataEventBus
          -> inline consolidated view + strategy
          -> bounded async recorder/side output
```

`BookRecoveryPolicy`, `RecoveryCoordinator`, `SessionHealth`, and `StaleWatchdog` separate recovery scheduling and health from transport callbacks. `RawJournalWriter` and `RawReplayProcessor` own segmented persistence and deterministic replay. Legacy benchmarks and scripts remain available.
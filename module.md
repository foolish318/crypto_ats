# Data Source Module Design

This document records the design direction for the market-data source module. The goal is to make exchange data ingestion more professional while keeping the current Java learning project understandable.

## Scope

The data source module covers:

```text
exchange REST API
exchange WebSocket API
direct exchange FIX API
third-party normalized data providers
local normalization, sequencing, order-book building, and fan-out to processors
```

It does not cover order execution yet. Order entry will be designed separately because private auth, risk checks, order state, rejects, and fills have a different lifecycle from public market data.

## References

Open-source design references:

```text
Hummingbot:
  Connector architecture separates exchange connector, order-book tracker,
  order-book data source, user stream data source, and order tracker.
  https://hummingbot.org/connectors/connectors/architecture/

Hummingbot connector requirements:
  REST is used for trading rules, status, snapshots, and backup state.
  WebSocket is used for public order-book/trade streams and private order updates.
  https://hummingbot.org/connectors/connectors/build/

XChange:
  Java library with a unified exchange API across many crypto exchanges.
  Useful as a correctness/reference path, but not the best low-latency path.
  https://github.com/knowm/XChange

CCXT:
  Unified public/private exchange API. REST is open-source; WebSocket support
  is handled by CCXT Pro.
  https://github.com/ccxt/ccxt/wiki/manual
```

Exchange protocol references:

```text
Binance.US:
  REST depth snapshots plus WebSocket bookTicker, partial depth, and diff depth.
  Local book management requires buffering deltas, fetching a REST snapshot,
  dropping stale updates, then applying sequential deltas.
  https://github.com/binance-us/binance-us-api-docs/blob/master/web-socket-streams.md

OKX:
  Public market data is available through REST and WebSocket. books5 is a
  top-5 snapshot channel; books/books-l2-tbt are incremental order-book feeds.
  https://www.okx.com/docs-v5/en/

Kraken:
  WebSocket v2 book channel streams L2 order-book data with configurable depth
  and checksum support.
  https://docs-legacy.kraken.com/api/docs/websocket-v2/book/
```

FIX and third-party data references:

```text
Coinbase Exchange:
  Provides REST, WebSocket market data, FIX order entry, and FIX market data.
  https://docs.cdp.coinbase.com/exchange/introduction/welcome

Binance Spot global:
  FIX market-data sessions exist for the global spot exchange and require
  API keys with FIX permissions. This is not the same as Binance.US.
  https://github.com/binance/binance-spot-api-docs/blob/master/fix-api.md

Gemini:
  Public docs expose FIX API reference. Account/API access is required for
  private or institutional workflows.
  https://developer.gemini.com/fix-api/fix-api

Kraken:
  Public API center lists REST, WebSocket, and FIX for spot/futures.
  https://docs.kraken.com/

CoinAPI:
  Third-party unified crypto data provider. REST and WebSocket are documented
  for market data; FIX 4.4 is available as a market-data alternative to WebSocket.
  https://www.coinapi.io/products/market-data-api/docs
  https://www.coinapi.io/products/market-data-api/docs/fix

dxFeed:
  Third-party market-data provider with REST, WebSocket, FIX, and Java/C++ APIs.
  https://kb.dxfeed.com/en/market-data-api/data-access-solutions.html

Databento:
  Third-party normalized market-data provider with live/historical feeds,
  unified schemas, and low-latency normalization claims. More relevant for
  futures/options/equities than spot crypto.
  https://databento.com/docs
```

## Current State

Current active code already has useful boundaries:

```text
com.example.hft.exchange
  Shared REST/WebSocket adapter base classes.

com.example.hft.exchange.binance
com.example.hft.exchange.okx
com.example.hft.exchange.kraken
  Exchange-specific parsers/adapters.

com.example.hft.marketdata.model
  Quote, depth, top-of-book, and local order-book models.

com.example.hft.pipeline
  Queue/ring-buffer processing and latency measurement.
```

The main gap is that REST, WebSocket, and future FIX data are not yet modeled as one professional ingestion module. Today each runnable example reaches into adapters directly. The next design should make data sources plug into one common contract.

## Design Principles

1. Keep exchange-specific code isolated.

Each exchange has different symbols, timestamps, sequence IDs, checksums, reconnect rules, and JSON shapes. Those differences should stay inside one connector package.

2. Normalize before strategy.

The strategy layer should not know whether a quote came from Binance JSON, Kraken JSON, OKX JSON, or FIX tags. It should consume canonical Java records.

3. Use REST for bootstrap and recovery, not the main live signal.

REST is useful for metadata, trading rules, snapshots, validation, and gap recovery. WebSocket or FIX should provide live market-data updates.

4. Build local order books per exchange and symbol.

For cross-exchange arbitrage, each venue keeps its own book. The aggregator compares normalized books after each venue is internally consistent.

5. Track data quality explicitly.

Every event should carry source, transport, receive time, exchange time if present, sequence/update ID if present, and a quality status.

6. Measure each stage separately.

End-to-end time is not enough. We need network receive, parse, queue wait, book update, strategy decision, and fan-out timing.

## Target Flow

```text
Exchange REST snapshot
Exchange WebSocket stream
Exchange FIX session
Third-party provider stream
        |
        v
Transport client
        |
        v
RawInboundMessage
        |
        v
Exchange parser / normalizer
        |
        v
NormalizedMarketDataEvent
        |
        v
Per exchange-symbol sequencer
        |
        v
LocalOrderBook
        |
        v
TopOfBook / DepthView / TradeTick
        |
        v
Strategy and benchmark pipeline
```

## Proposed Package Layout

```text
com.example.hft.datasource
  MarketDataConnector
  MarketDataSubscription
  MarketDataSink
  DataSourceHealth
  DataSourceStatus

com.example.hft.datasource.transport
  RestTransport
  WebSocketTransport
  FixTransport
  TransportType
  RawInboundMessage

com.example.hft.datasource.normalizer
  MarketDataNormalizer
  NormalizedMarketDataEvent
  BookSnapshotEvent
  BookDeltaEvent
  TradeEvent
  TopOfBookEvent

com.example.hft.datasource.book
  BookCoordinator
  BookSequencer
  BookResyncPolicy
  BookQuality

com.example.hft.exchange.binance
com.example.hft.exchange.okx
com.example.hft.exchange.kraken
  Exchange-specific connector, parser, symbol mapping, and resync rules.

com.example.hft.exchange.coinapi
com.example.hft.exchange.dxfeed
  Future third-party normalized provider adapters.
```

## Core Interfaces

The connector is the outside-facing unit. One connector represents one venue or provider.

```java
public interface MarketDataConnector extends AutoCloseable {
    String name();

    DataSourceStatus status();

    void subscribe(MarketDataSubscription subscription, MarketDataSink sink);

    TopOfBookSnapshot fetchTopOfBook(String symbol) throws Exception;

    BookSnapshotEvent fetchBookSnapshot(String symbol, int depth) throws Exception;
}
```

The sink is the handoff from data source into the pipeline.

```java
public interface MarketDataSink {
    void onEvent(NormalizedMarketDataEvent event);

    void onHealth(DataSourceHealth health);

    void onError(String source, Throwable error);
}
```

The raw message is captured before parsing so we can benchmark and replay.

```java
public record RawInboundMessage(
        String source,
        String exchange,
        String symbol,
        TransportType transport,
        String channel,
        long receivedNanos,
        long exchangeTimeMillis,
        long sequence,
        byte[] payload
) {
}
```

## REST, WebSocket, and FIX Roles

REST:

```text
Best use:
  metadata, active symbols, trading rules, depth snapshots, validation, recovery

Weakness:
  polling latency, rate limits, missing intra-poll events

In our module:
  RestTransport fetches snapshots and metadata.
  BookCoordinator uses REST snapshots for bootstrap and resync.
```

WebSocket:

```text
Best use:
  live public market data, trades, book deltas, top-of-book updates

Weakness:
  disconnects, heartbeat handling, exchange-specific sequence logic,
  JSON parse overhead

In our module:
  WebSocketTransport owns connection lifecycle.
  Exchange parser converts JSON messages into canonical events.
```

FIX:

```text
Best use:
  institutional-style sessions, lower-latency market data where available,
  stricter sequence/session semantics, direct exchange or provider access

Weakness:
  usually needs account onboarding or paid access, more complex protocol,
  message dictionaries, heartbeats, resend/sequence rules

In our module:
  FixTransport should be hidden behind the same MarketDataConnector interface.
  QuickFIX/J is the likely Java starting point.
```

Third-party normalized data:

```text
Best use:
  faster multi-exchange coverage, historical data, backtesting, normalized schemas,
  data quality checks, provider-managed exchange integration

Weakness:
  may add provider hop latency, usually needs paid subscription, source rules
  and timestamps must be understood carefully

In our module:
  Treat provider feeds as another connector, not as a replacement for direct feeds.
```

## Local Order Book Design

For each `exchange + symbol`:

```text
1. Open WebSocket or FIX stream.
2. Buffer incoming deltas.
3. Fetch REST or provider snapshot.
4. Drop stale deltas older than the snapshot sequence.
5. Apply the first delta that bridges the snapshot sequence.
6. Apply future deltas only if sequence continuity is valid.
7. If a gap, checksum failure, stale feed, or crossed book appears, mark the book degraded.
8. Trigger resync with a fresh snapshot.
```

This is directly aligned with Binance-style snapshot plus diff-depth logic and Hummingbot-style `OrderBookTrackerDataSource -> OrderBookTracker` separation.

## Fan-In for Multi-Exchange Data

For cross-exchange comparison, keep each venue independent until its book is valid.

```text
Binance.US BTCUSDT book
OKX BTC-USDT book
Kraken BTC/USD book
        |
        v
Canonical symbol mapper
        |
        v
CrossExchangeMarketView BTC/USD
        |
        v
Strategy:
  compare bid/ask, depth-weighted spread, top5/top10 liquidity,
  stale data, fees, and transfer/trading constraints
```

Do not merge raw books into one book. A Binance bid and Kraken bid are different liquidity pools with different fees, latency, and fill probability.

## Latency Measurements

Every event should carry timestamps for:

```text
exchangeEventTimeMillis:
  Time assigned by the exchange/provider, if available.

receivedNanos:
  Local monotonic time when bytes/message were received.

parsedNanos:
  Local monotonic time after JSON/FIX parsing.

enqueuedNanos:
  Time when normalized event entered the queue.

dequeuedNanos:
  Time when processor consumed the event.

bookAppliedNanos:
  Time after local order book update finished.

strategyDoneNanos:
  Time after decision logic finished.
```

Metrics to print:

```text
network lag:
  local receive time minus exchange event time, only meaningful if clocks are sane.

parse latency:
  parsedNanos - receivedNanos

queue latency:
  dequeuedNanos - enqueuedNanos

book latency:
  bookAppliedNanos - dequeuedNanos

strategy latency:
  strategyDoneNanos - bookAppliedNanos

local pipeline latency:
  strategyDoneNanos - receivedNanos
```

## Data Quality Checks

Required checks:

```text
sequence gap detection
duplicate event detection
out-of-order event detection
stale feed detection
REST snapshot age
crossed book detection
negative or zero price validation
negative size validation
checksum validation where exchange provides checksum
symbol mapping validation
exchange status / maintenance handling
rate-limit handling
reconnect and resubscribe behavior
```

## Recommended Implementation Phases

V14 - Data-source interfaces:

```text
Add MarketDataConnector, MarketDataSink, MarketDataSubscription,
RawInboundMessage, TransportType, and NormalizedMarketDataEvent.
Move existing Binance.US, OKX, and Kraken WebSocket adapters behind the interface.
```

V15 - Local book coordinator:

```text
Implement REST snapshot bootstrap plus WebSocket delta application for one exchange.
Start with Binance.US depth because its local-book algorithm is clearly documented.
```

V16 - Multi-exchange market view:

```text
Keep one local book per exchange/symbol.
Add symbol mapping into canonical BTC/USD, ETH/USD style names.
Compare top-of-book, top5/top10 depth, and stale-book status.
```

V17 - Recorder and replay:

```text
Persist RawInboundMessage or normalized events to local files.
Run the same pipeline against captured live data for repeatable benchmarks.
```

V18 - FIX adapter experiment:

```text
Pick one accessible provider or exchange.
Likely candidates:
  CoinAPI FIX for third-party normalized crypto market data.
  Coinbase Exchange FIX Market Data if account access is available.
  Gemini/Kraken/Binance global FIX only if account/API permissions allow it.
Use QuickFIX/J behind FixTransport.
```

## Practical Recommendation

For this project, the next useful optimization is not adding more exchanges. It is making the ingestion path consistent:

```text
MarketDataConnector
  -> RawInboundMessage
  -> NormalizedMarketDataEvent
  -> BookSequencer
  -> LocalOrderBook
  -> Strategy pipeline
```

After that, adding Binance.US, OKX, Kraken, Gemini, Coinbase, CoinAPI, or FIX becomes a connector problem instead of a whole-system rewrite.


## V14 Implementation Status

Implemented in this project:

```text
com.example.hft.datasource
  MarketDataConnector
  MarketDataSubscription
  MarketDataSink
  DataSourceHealth
  DataSourceStatus
  TopOfBookMarketDataConnector

com.example.hft.datasource.transport
  TransportType
  RawInboundMessage

com.example.hft.datasource.normalizer
  NormalizedMarketDataEvent
  TopOfBookEvent
  BookDepthEvent
  TradeEvent

com.example.hft.datasource.book
  BookSequencer
  BookQuality
```

The current `custom-ws-vs-baseline` app now uses `MarketDataConnector` wrappers around existing Binance.US, OKX, and Kraken WebSocket/REST adapters. The exchange-specific parser code remains in the exchange packages.

Colored local diagram:

```text
docs/architecture.md
```

## Replay Module

A replay module lets us run the same strategy pipeline against previously recorded market data instead of always connecting to live exchanges.

Why it matters:

```text
Live exchange data changes every run, so benchmark results are hard to compare.
Replay gives us repeatable input, so V2/V3/V4/V5/V14 can be compared on the same event sequence.
Replay also lets us debug parser, queue, book, and strategy logic without depending on network availability.
```

Replay design:

```text
Recorder
  writes RawInboundMessage and/or NormalizedMarketDataEvent to local files

ReplayTransport
  reads those files back in timestamp order

ReplayMarketDataConnector
  exposes replayed events through the same MarketDataConnector / MarketDataSink contract

Strategy Pipeline
  receives replayed events as if they came from WebSocket or FIX
```

Replay should preserve:

```text
source exchange
symbol
channel
transport type
exchange event time
local receive time
sequence/update id
raw payload
normalized event fields
```

Next implementation step after V14:

```text
V15 should add a Recorder and ReplayTransport around Binance.US depth data.
Then benchmark live WebSocket processing versus replayed local-file processing.
```
## V15 Reference-Inspired Data Module Cleanup

This update compares the project against mature open-source trading frameworks and adds the missing boundaries that are common in those systems.

What we copied from other projects:

```text
Hummingbot:
  Connector + OrderBookTrackerDataSource + OrderBookTracker separation.
  Lesson for us: data retrieval, book maintenance, and strategy access should be separate responsibilities.

NautilusTrader:
  Adapter/DataClient -> DataEngine -> Cache -> MessageBus -> Strategy flow.
  Lesson for us: market data should be cached before strategies consume it, and publication should go through one data-engine boundary.

XChange / CCXT:
  Unified exchange-facing API over many exchange implementations.
  Lesson for us: strategy and app code should depend on a stable connector interface, not specific Binance/OKX/Kraken classes.
```

New Java package boundaries added:

```text
com.example.hft.datasource.instrument
  Instrument
  InstrumentProvider
  StaticInstrumentProvider
  SymbolMapper

com.example.hft.datasource.engine
  MarketDataEngine
  MarketDataCache
  MarketDataEventBus
  MarketDataListener

com.example.hft.datasource.replay
  RecordingMarketDataSink
  ReplayMarketDataSource
  ReplayRecord
  ReplayClockMode
```

Updated target flow:

```text
External source
  -> MarketDataConnector / MarketDataClient
  -> Transport client
  -> RawInboundMessage
  -> Parser / Normalizer
  -> NormalizedMarketDataEvent
  -> MarketDataEngine
  -> MarketDataCache + MarketDataEventBus
  -> BookCoordinator / BookSequencer / LocalOrderBook
  -> CrossExchangeMarketView
  -> Strategy / Benchmark / Replay runner
```

Why this is more professional:

```text
Instrument metadata is no longer mixed with parser code.
Market-data ingestion has a single engine boundary.
Strategies can read latest state from cache and subscribe to events.
Replay is treated as a first-class source, not a special benchmark hack.
The diagram now uses aligned columns and orthogonal arrows so it reads like an architecture document, not a sketch.
```

Updated image files:

```text
docs/architecture.png
docs/architecture.svg
docs/architecture.md
```
## V16 Runtime Wiring

V16 moves the architecture from skeleton to active runtime path for the multi-exchange top-of-book validation app.

Active path now used by `CustomWebSocketVsBaselineTopOfBookMain`:

```text
MarketDataConnector.subscribe(...)
  -> FanoutMarketDataSink
  -> MarketDataEngine.onEvent(...)
  -> MarketDataCache.update(...)
  -> MarketDataEventBus.publish(...)
  -> RecordingMarketDataSink.onEvent(...)
  -> REST / XChange baseline comparison reads WebSocket result from MarketDataCache
```

What changed in practice:

```text
The app no longer treats the WebSocket adapter result as a one-off local variable.
The WebSocket event is normalized into TopOfBookEvent.
MarketDataEngine caches the latest top-of-book per exchange + symbol.
MarketDataEventBus publishes the normalized event to subscribed listeners.
RecordingMarketDataSink captures the same normalized events for future replay.
SymbolMapper maps exchange-specific symbols like BTCUSDT and BTC-USDT to canonical BTC/USD.
```

Current limitation:

```text
This is still top-of-book validation, not full depth local-book maintenance.
The next real implementation step is BookCoordinator: REST snapshot bootstrap + WebSocket delta sequencing + LocalOrderBook update.
```
## V16 Version and Validation

Code version marker:

```text
DataSourceModuleVersion.VERSION = V16-data-engine-runtime
```

Latest validation result:

```text
V14-connector-wrapper: wsCount=6 avgWsLoadMs=1329.626667 restExact=5/6 xchangeExact=3/4
V16-data-engine-runtime: wsCount=6 avgWsLoadMs=2108.258333 avgEngineEtlUs=433.300000 restExact=6/6 xchangeExact=4/4 cacheTopOfBook=6 publishedEvents=6 replayRecords=6
```

Quality interpretation:

```text
Both versions loaded data successfully from all 6 exchange/symbol connectors.
V16 confirms the refactored runtime path is active because cache, event bus, and replay recorder all received the expected 6 events.
The live load-time difference should not be read as a regression without replay, because these runs include network and market-data arrival timing.
```
## V17 Raw Depth To Local Order Book

V17 implements the first real full-depth market-data stage for Binance.US.

Implemented path:

```text
Binance.US WebSocket combined stream
  -> RawDepthPayload queue
  -> raw JSONL recorder
  -> BinanceDepthParser
  -> SequencedLocalOrderBook
  -> LocalOrderBook per symbol
  -> book event JSONL + summary JSON
```

Snapshot/bootstrap path:

```text
WebSocket is opened first.
Raw events are buffered locally.
REST depth snapshot is fetched with limit=5000.
The snapshot lastUpdateId anchors the local book.
Buffered and live events are then applied only if their U/u sequence is valid.
```

New code:

```text
src/main/java/com/example/hft/app/BinanceRawDepthOrderBookMain.java
src/main/java/com/example/hft/datasource/book/SequencedLocalOrderBook.java
src/main/java/com/example/hft/datasource/book/DepthUpdateApplyResult.java
src/main/java/com/example/hft/marketdata/model/LocalOrderBook.java
```

Quality metrics now captured:

```text
rawMessages      every WebSocket message accepted by the local producer
parsed           raw JSON messages successfully parsed into DepthUpdate
applied          valid depth deltas applied to the local book
stale            old updates safely ignored because u <= lastUpdateId
gaps             sequence discontinuities, meaning the local book should be treated as invalid until resync
crossed          bid >= ask after applying an update
parseFailures    malformed or unexpected raw payloads
```

Important fix in this version:

```text
LocalOrderBook price and size scaling were increased for crypto decimals.
The old cent-level price scale could incorrectly report XRPUSDT as crossed because bid and ask collapsed into the same integer tick.
```

Reference rule:

```text
Binance.US documents the same local order-book algorithm: open the stream, buffer events, fetch depth snapshot, drop stale events, bridge lastUpdateId + 1, then apply continuous updates.
```
## V18 Automatic Reconnect And Resync

V18 implements the first production hardening item only.

Behavior:

```text
WebSocket failure -> reconnect stream -> reload snapshots for all symbols.
Book GAP or CROSSED -> reload snapshot for that symbol -> continue processing later raw messages.
Replay mode stays deterministic and does not fetch live REST snapshots.
```

New operational counters:

```text
reconnectAttempts
reconnectSuccesses
reconnectFailures
resyncAttempts
resyncSuccesses
resyncFailures
```
## V19 Multi-Exchange Deep Book Sources

V19 adds a deep-book datasource catalog and live validation app.

New package:

```text
com.example.hft.datasource.deepbook
  DeepBookSourceDefinition
  DeepBookSourceCatalog
```

New runnable app:

```text
com.example.hft.app.DeepBookSourceDiscoveryMain
```

Current validated public sources:

```text
Binance.US:
  REST /api/v3/depth?limit=5000 plus WebSocket depth@100ms.
  This remains the local-book source used by V18.

OKX:
  Public WebSocket books channel.
  400 depth levels, incremental order book, public access.
  Faster tick-by-tick 400/50-level channels require login and VIP tier.

Kraken:
  Public WebSocket v2 book channel.
  Supports 10/25/100/500/1000 depth levels.
  Current catalog uses 1000 levels for BTC/USD and ETH/USD.
```

Boundary:

```text
V19 adds source discovery and validation.
It does not yet normalize OKX/Kraken into maintained local books.
The next step is exchange-specific book builders: OKX seqId/prevSeqId, Kraken checksum, and reconnect/resubscribe rules.
```
## V21 Accepted Local-Book Runtime

The current implementation now follows the target source-module architecture:

```text
Depth source
  -> RawEnvelope and AsyncRawRecorder
  -> LiveBookSession
  -> exchange-specific LocalOrderBookBuilder
  -> quality/state/freshness gate
  -> AcceptedLocalBookEvent
  -> MarketDataEngine
  -> deep-book cache and event-bus consumers
```

`LiveBookSession` owns one exchange-symbol lifecycle. `SessionHealth` separates transport, book, and session states. `StaleWatchdog` detects silence. `RecoveryCoordinator` owns generation-isolated reconnects with jittered exponential backoff. `LocalBookPublisher` is the only accepted-event handoff.

Raw replay is production-shaped: REST snapshots, WebSocket messages, connect/disconnect/recovery records, generation, source identity, and receive clocks are retained. Recorder loss marks the file replay-unsafe. `RawReplayProcessor` recreates the same venue builders and validates final-book parity.

Current downstream consumers are intentionally small examples: `AcceptedBookEventRecorder`, `CrossExchangeBookView`, and `DeepBookStrategyListener`. They demonstrate that strategy code receives accepted canonical books without knowing venue JSON or recovery details.

## V22 P0 Runtime Hardening

The data-source module now validates public instrument status and increments before connecting, separates protocol control messages from book data, and hands WebSocket book events to a bounded source-partition dispatcher. Subscription ACK, heartbeat, ping/pong timeout, Binance connection-age rotation, bootstrap overflow, dispatcher pressure, and task failures are observable in the summary.

`LOT_SIZE` is retained as order-entry metadata. The market-data quality gate validates incoming price tick alignment but does not incorrectly require an aggregated depth quantity to be an order-lot multiple.

Raw recording occurs at connector ingress. Both recorder loss and processing loss make replay unsafe. `IncrementalRawReplayProcessor` supports identical event-by-event processing in deterministic replay and latency benchmarks.

## V23 Direct Single-Writer Runtime

The default live module now hands each recorded ingress envelope directly to `VenueSessionProtocol` and its source-owned `LiveBookSession`. Each mutable local book has one sequential writer and no application processing queue. This preserves protocol acknowledgement, heartbeat, metadata, checksum, bootstrap, recovery, watchdog, accepted-event, and replay behavior from V22 while removing queue wait from the normal receive-to-book path.

`PartitionedBookEventDispatcher` and `DeepBookReplayBenchmark` remain available for identical-record capacity experiments. They are not dependencies of `MultiExchangeLocalBookMain`. `AsyncRawRecorder` remains asynchronous because durable evidence is a side-path responsibility and must not block book mutation.

The complete framework comparison and the conditions for adding Aeron, Disruptor, Chronicle Queue, Kafka, Redis, SBE, extra JVMs, or extra hosts are maintained in [`reference-frameworks.md`](reference-frameworks.md).

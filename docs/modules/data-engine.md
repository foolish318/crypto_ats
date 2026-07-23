# Market-Data Engine Module

![Market-data engine module](data-engine.svg)

PNG fallback: [data-engine.png](data-engine.png)

The engine is the accepted-event boundary between validated local books and downstream consumers.

## Ordering Contract

```text
AcceptedLocalBookEvent
  -> MarketDataEngine.onEvent
  -> MarketDataCache.update
       key = exchange + symbol
       value = latest accepted deep book
  -> MarketDataEventBus.publish
       -> AcceptedBookEventRecorder
       -> CrossExchangeBookView
       -> DeepBookStrategyListener
```

Cache-first ordering lets a listener query coherent latest state while handling the event. `MarketDataCache` retains the legacy top-of-book path and now also supports `deepBook(exchange, symbol)`.

## Boundary Rule

`LocalBookPublisher` constructs an accepted event only when the builder result is accepted and `LIVE`, all three session states are publishable, and message age is below the threshold. `REJECT`, `STALE`, `BOOTSTRAPPING`, disconnected, recovering, and stopped states cannot reach the engine.

## Current Code

```text
src/main/java/com/example/hft/datasource/engine/
src/main/java/com/example/hft/datasource/deepbook/runtime/AcceptedLocalBookEvent.java
src/main/java/com/example/hft/datasource/deepbook/runtime/LocalBookPublisher.java
src/main/java/com/example/hft/datasource/deepbook/runtime/CrossExchangeBookView.java
src/main/java/com/example/hft/datasource/deepbook/runtime/DeepBookStrategyListener.java
```

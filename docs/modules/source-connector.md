# Source And Connector Module

![Source and connector module](source-connector.svg)

PNG fallback: [source-connector.png](source-connector.png)

The connector boundary keeps exchange-specific URLs, subscriptions, symbol formats, and lifecycle state out of consumers.

## Responsibilities

```text
InstrumentProvider  -> instrument metadata and canonical symbol mapping
MarketDataConnector -> connect, subscribe, stop, status, and health
MarketDataClient    -> own REST/WebSocket sessions and emit raw messages
```

Current direct public sources are Binance.US, OKX, and Kraken. Third-party and FIX sources should implement the same connector contract rather than create a parallel pipeline.

## Current Code

```text
src/main/java/com/example/hft/datasource/
src/main/java/com/example/hft/datasource/instrument/
src/main/java/com/example/hft/exchange/
```

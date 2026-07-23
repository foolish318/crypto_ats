# Source And Connector

![Source and connector](source-connector.svg)

`DeepBookSourceCatalog` defines the current six public depth feeds. Each `DeepBookSourceDefinition` owns venue identity, symbol, channel, depth, WebSocket URI, optional subscription payload, and optional REST snapshot URI.

Binance.US uses a REST snapshot bridged to buffered WebSocket deltas. OKX and Kraken receive ordered snapshot/update messages over WebSocket. Venue symbols are mapped to canonical instruments before publication; missing or invalid metadata fails closed.
# Data Source Module Diagram

This is the V15 reference-inspired shape for the data-source module. The PNG/SVG image uses aligned columns and orthogonal arrows to show external sources, venue adapter, data intake, data engine, book state, and consumers.

Actual image files:

```text
docs/data-source-architecture.png
docs/data-source-architecture.svg
```

![Data Source Architecture](data-source-architecture.png)

```mermaid
%%{init: {"theme": "base", "themeVariables": {"fontFamily": "Inter, Arial, sans-serif", "primaryTextColor": "#111827", "lineColor": "#6b7280"}}}%%
flowchart LR
    subgraph S["Source Protocols"]
        REST["Exchange REST<br/>snapshot / metadata / recovery"]
        WS["Exchange WebSocket<br/>live JSON market data"]
        FIX["Exchange FIX<br/>future low-latency feed"]
        THIRD["Third-party Provider<br/>CoinAPI / dxFeed / Databento"]
        REPLAY["Replay Files<br/>recorded raw or normalized events"]
    end

    subgraph T["Transport Layer"]
        RT["RestTransport"]
        WT["WebSocketTransport"]
        FT["FixTransport"]
        RPT["ReplayTransport"]
    end

    RAW["RawInboundMessage<br/>source, exchange, symbol, channel,<br/>transport, receivedNanos, sequence, payload"]

    subgraph N["Parser / Normalizer"]
        BP["Binance Parser"]
        OP["OKX Parser"]
        KP["Kraken Parser"]
        FP["FIX Parser"]
        PP["Provider Parser"]
    end

    EVENT["NormalizedMarketDataEvent<br/>TopOfBookEvent / BookDepthEvent / TradeEvent"]

    subgraph Q["Book Sequencing + Quality Gate"]
        SEQ["BookSequencer<br/>sequence / duplicate / gap checks"]
        QUAL["BookQuality<br/>LIVE / STALE / GAP / CROSSED / DEGRADED"]
    end

    subgraph B["Local Books"]
        BB["Binance BTC/USD Book"]
        OB["OKX BTC/USD Book"]
        KB["Kraken BTC/USD Book"]
    end

    VIEW["CrossExchangeMarketView<br/>canonical symbol mapping + venue comparison"]

    STRAT["Strategy Pipeline<br/>spread, top5/top10 liquidity, latency stats"]

    REC["Recorder<br/>write raw + normalized events"]

    REST --> RT
    WS --> WT
    FIX --> FT
    THIRD --> RT
    THIRD --> WT
    THIRD --> FT
    REPLAY --> RPT

    RT --> RAW
    WT --> RAW
    FT --> RAW
    RPT --> RAW

    RAW --> BP
    RAW --> OP
    RAW --> KP
    RAW --> FP
    RAW --> PP

    BP --> EVENT
    OP --> EVENT
    KP --> EVENT
    FP --> EVENT
    PP --> EVENT

    EVENT --> SEQ
    SEQ --> QUAL
    QUAL --> BB
    QUAL --> OB
    QUAL --> KB

    BB --> VIEW
    OB --> VIEW
    KB --> VIEW
    VIEW --> STRAT

    RAW --> REC
    EVENT --> REC
    REC -.-> REPLAY

    classDef source fill:#e0f2fe,stroke:#0369a1,stroke-width:1px,color:#0f172a;
    classDef transport fill:#dcfce7,stroke:#15803d,stroke-width:1px,color:#052e16;
    classDef raw fill:#fef3c7,stroke:#b45309,stroke-width:1px,color:#451a03;
    classDef parser fill:#ede9fe,stroke:#7c3aed,stroke-width:1px,color:#2e1065;
    classDef quality fill:#ffe4e6,stroke:#be123c,stroke-width:1px,color:#4c0519;
    classDef book fill:#f1f5f9,stroke:#475569,stroke-width:1px,color:#0f172a;
    classDef strategy fill:#ccfbf1,stroke:#0f766e,stroke-width:1px,color:#042f2e;
    classDef replay fill:#fae8ff,stroke:#a21caf,stroke-width:1px,color:#4a044e;

    class REST,WS,FIX,THIRD source;
    class RT,WT,FT,RPT transport;
    class RAW raw;
    class BP,OP,KP,FP,PP parser;
    class SEQ,QUAL quality;
    class BB,OB,KB,VIEW book;
    class STRAT strategy;
    class REPLAY,REC replay;
```


# Current Architecture Diagram

Release `1.1.0` has one canonical architecture. The rendered image is [docs/architecture.svg](docs/architecture.svg).

```mermaid
flowchart LR
  subgraph S[Public Venue Sources]
    BD[Binance.US Depth]
    OD[OKX Depth]
    KD[Kraken Depth]
    BT[Binance.US Trades]
    OT[OKX Trades]
    KT[Kraken Trades]
  end

  subgraph R[Raw Ingestion]
    W[Generation-Fenced WebSocket]
    RJ[Bounded Raw Journal]
  end

  subgraph B[Book Pipeline]
    BP[Protocol + Snapshot Bridge]
    V[Sequence / Checksum / Quality]
    L[Single-Writer Local L2 Book]
    BS[Canonical BookSnapshot<br/>bookVersion + health]
  end

  subgraph T[Trade Pipeline]
    TN[Venue Trade Normalizers]
    TD[Duplicate + Out-of-Order Metrics]
    PT[Canonical PublicTrade]
  end

  subgraph P[Strategy Market Data Boundary]
    MP[StrategyMarketDataPort]
    OV[Immutable OrderBookView]
    MV[MultiVenueBookView]
    PN[Book / Trade / Status Push]
  end

  subgraph Q[Deterministic Research]
    NJ[Bounded Normalized Event Log]
    NR[Streaming Normalized Replay]
  end

  BD --> W
  OD --> W
  KD --> W
  BT --> W
  OT --> W
  KT --> W
  W --> RJ
  W --> BP
  BP --> V
  V --> L
  L --> BS
  W --> TN
  TN --> TD
  TD --> PT
  BS --> MP
  PT --> MP
  MP --> OV
  MP --> MV
  MP --> PN
  MP --> NJ
  NJ --> NR
  NR --> MP

  classDef source fill:#e0f2fe,stroke:#0284c7,color:#075985
  classDef raw fill:#fef3c7,stroke:#d97706,color:#78350f
  classDef book fill:#ede9fe,stroke:#7c3aed,color:#4c1d95
  classDef trade fill:#ffe4e6,stroke:#e11d48,color:#881337
  classDef api fill:#ccfbf1,stroke:#0f766e,color:#134e4a
  classDef replay fill:#fae8ff,stroke:#a21caf,color:#701a75
  class BD,OD,KD,BT,OT,KT source
  class W,RJ raw
  class BP,V,L,BS book
  class TN,TD,PT trade
  class MP,OV,MV,PN api
  class NJ,NR replay
```

The pipeline publishes data only. Arbitrage, market making, Alpha, fair value, execution, positions, and risk remain downstream and out of scope.
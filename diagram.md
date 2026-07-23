# Current Architecture Diagram

Release `1.0.0` has one canonical architecture. The rendered image is [docs/architecture.svg](docs/architecture.svg); detailed module images are under `docs/modules/`.

```mermaid
flowchart LR
  subgraph S[Market Sources]
    B[Binance.US<br/>REST snapshot + WebSocket]
    O[OKX<br/>WebSocket snapshot + updates]
    K[Kraken<br/>WebSocket snapshot + updates]
  end

  subgraph I[Intake And Recovery]
    T[VenueTransport]
    P[Protocol State Machine]
    H[Session Health + Watchdog]
    R[Recovery Coordinator<br/>generation + backoff]
  end

  subgraph L[Deterministic Local Book]
    J[RawEnvelope]
    Q[Segmented Journal]
    M[Single-Writer Mutation]
    G[Continuity + Checksum<br/>Quality Gate]
    A[AcceptedLocalBookEvent]
  end

  subgraph D[Distribution]
    E[MarketDataEngine]
    C[Generation-Fenced Cache]
    U[Bounded Event Bus]
  end

  subgraph X[Consumers]
    V[Consolidated Book<br/>NBBO + coherence]
    Y[Strategy]
    Z[Async Recorder / Analytics]
  end

  B --> T
  O --> T
  K --> T
  T --> J
  J --> Q
  J --> P
  P --> M
  H --> R
  R --> T
  M --> G
  G -->|ACCEPT| A
  G -->|STALE / INVALID| H
  A --> E
  E --> C
  E --> U
  U --> V
  U --> Y
  U --> Z
  H -->|availability event| E

  classDef source fill:#e0f2fe,stroke:#0284c7,color:#075985
  classDef intake fill:#fef3c7,stroke:#d97706,color:#78350f
  classDef book fill:#ede9fe,stroke:#7c3aed,color:#4c1d95
  classDef engine fill:#ccfbf1,stroke:#0f766e,color:#134e4a
  classDef consumer fill:#dcfce7,stroke:#16a34a,color:#14532d
  class B,O,K source
  class T,P,H,R intake
  class J,Q,M,G,A book
  class E,C,U engine
  class V,Y,Z consumer
```

The critical rule is fail closed: leaving `LIVE` immediately tombstones the source in cache and removes it from consolidated state. Only an accepted event from the current generation can make the source usable again.
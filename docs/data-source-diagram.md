# Data Source Module Diagrams

The current design is V20. It adds an explicit market-data quality boundary between venue parsing and downstream local-book publication.

## V20 Pipeline

![V20 multi-exchange data pipeline](data-source-architecture-v20.svg)

## V20 Quality Gate

![V20 venue-specific quality gate](data-quality-gate-v20.svg)

Detailed rules, failure behavior, source references, and output fields are documented in [data-quality-v20.md](data-quality-v20.md).

```mermaid
flowchart LR
    subgraph S["Live Sources"]
        BIN["Binance.US<br/>REST snapshot + WS U/u"]
        OKX["OKX books<br/>prevSeqId + seqId"]
        KRK["Kraken book v2<br/>CRC32"]
    end

    RAW["Raw payload<br/>receive time + source"]
    PARSE["Exact decimal parser<br/>temporary venue book"]

    subgraph Q["V20 Quality Gate"]
        COMMON["Common checks<br/>schema, values, order,<br/>freshness, uncrossed"]
        VENUE["Venue checks<br/>sequence or checksum"]
    end

    ACCEPT["ACCEPT<br/>publish local book"]
    REJECT["REJECT<br/>suppress + resync"]
    CONSUMER["Cross-venue view<br/>strategy / replay / metrics"]

    BIN --> RAW
    OKX --> RAW
    KRK --> RAW
    RAW --> PARSE
    PARSE --> COMMON
    COMMON --> VENUE
    VENUE -->|all pass| ACCEPT
    VENUE -->|any fail| REJECT
    ACCEPT --> CONSUMER
    REJECT -.-> RAW

    classDef source fill:#e0f2fe,stroke:#0284c7,color:#0f172a;
    classDef intake fill:#fef3c7,stroke:#d97706,color:#0f172a;
    classDef quality fill:#ffe4e6,stroke:#e11d48,color:#4c0519;
    classDef accepted fill:#ccfbf1,stroke:#0f766e,color:#042f2e;
    classDef rejected fill:#fff1f2,stroke:#be123c,color:#4c0519;

    class BIN,OKX,KRK source;
    class RAW,PARSE intake;
    class COMMON,VENUE quality;
    class ACCEPT,CONSUMER accepted;
    class REJECT rejected;
```

## Earlier Reference Diagram

The broader V15 connector/data-engine/cache/replay reference remains available:

```text
docs/data-source-architecture.png
docs/data-source-architecture.svg
```

V20 does not remove those boundaries. It makes the previously conceptual `BookSequencer + BookQuality` block executable and venue-specific.

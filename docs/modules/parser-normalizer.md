# Parser And Normalizer Module

![Parser and normalizer module](parser-normalizer.svg)

PNG fallback: [parser-normalizer.png](parser-normalizer.png)

Venue parsers understand wire schemas. Normalized events expose stable Java contracts to quality, book, engine, and strategy modules.

## Canonical Events

```text
TopOfBookEvent
BookDepthEvent
TradeEvent
```

Normalization retains venue identity, canonical instrument identity, exact decimal values, event time, receive time, sequence fields, and snapshot/update type. Data needed for continuity or latency analysis must not be discarded during parsing.

Parse failures are counted and quarantined with source metadata; they do not mutate local-book state.

## Current Code

```text
src/main/java/com/example/hft/datasource/normalizer/
src/main/java/com/example/hft/datasource/instrument/SymbolMapper.java
src/main/java/com/example/hft/exchange/*/*Parser.java
```

# Transport And Raw Intake Module

![Transport and raw intake module](transport-intake.svg)

PNG fallback: [transport-intake.png](transport-intake.png)

This module converts protocol callbacks into an immutable intake envelope before venue parsing or state mutation.

## Raw Envelope

`RawInboundMessage` preserves payload, exchange, symbol, transport, receive time, and available sequence metadata. The raw recorder can therefore reproduce parser failures and compare later implementations against identical input.

REST snapshots and WebSocket updates have different timing and ordering semantics, but they meet at the same raw-message boundary. FIX or a third-party binary feed can join here later.

## Backpressure

A bounded handoff must expose its policy and metrics. A production connector cannot silently lose depth updates: it must block, reject with an observable counter, or degrade and rebuild the affected book.

## Current Code

```text
src/main/java/com/example/hft/datasource/transport/
src/main/java/com/example/hft/exchange/
```

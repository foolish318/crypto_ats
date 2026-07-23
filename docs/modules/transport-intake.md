# Transport And Raw Intake Module

![Transport and raw intake module](transport-intake.svg)

PNG fallback: [transport-intake.png](transport-intake.png)

This module records protocol callbacks as immutable intake envelopes before parsing or state mutation. V23 processes each source inline with single-writer ownership; only persistence remains asynchronous.

## Raw Envelope

`RawInboundMessage` preserves payload, exchange, symbol, transport, receive time, and available sequence metadata. The raw recorder can therefore reproduce parser failures and compare later implementations against identical input.

REST snapshots and WebSocket updates have different timing and ordering semantics, but they meet at the same raw-message boundary. FIX or a third-party binary feed can join here later.

## Backpressure

The current live path has no application processing queue: ingress records the raw envelope, then the venue protocol and book builder run inline. `PartitionedBookEventDispatcher` remains an explicit replay benchmark for future capacity testing. It must not return to the default path unless identical-record end-to-end measurements justify the queue cost.

## Current Code

```text
src/main/java/com/example/hft/datasource/transport/
src/main/java/com/example/hft/exchange/
```

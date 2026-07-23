# Recorder And Replay Module

![Recorder and replay module](recorder-replay.svg)

PNG fallback: [recorder-replay.png](recorder-replay.png)

Replay preserves enough source evidence to reconstruct the same final local book, not merely reparse selected WebSocket payloads.

## Raw Envelope

Each JSONL line has this schema:

```json
{
  "version": "V21-multi-exchange-local-books",
  "recordType": "REST_SNAPSHOT | WS_MESSAGE | CONNECT | DISCONNECT | RECOVERY",
  "generation": 1,
  "sourceId": "okx-BTC-USDT",
  "exchange": "OKX",
  "symbol": "BTC-USDT",
  "receivedEpochMillis": 0,
  "receivedNanos": 0,
  "payload": "...",
  "detail": "..."
}
```

Binance REST snapshots are written with `BEFORE_APPLY` and `AFTER_APPLY` details. Replay applies only the former, buffers pre-snapshot Binance deltas, then bridges them in recorded order. OKX and Kraken snapshot/update callbacks remain in original queue order. Generation changes start a fresh builder and isolate stale callbacks.

## Loss Contract

`AsyncRawRecorder` uses a bounded queue so disk I/O does not block the WebSocket callback. Queue overflow or writer failure is never hidden:

```text
droppedRecords > 0
replaySafe = false
firstDropEpochMillis and firstDropReason populated
REPLAY_UNSAFE marker appended when the writer drains
```

`RawReplayProcessor` rejects an unsafe file. A file with missing evidence must never look complete.

## Deterministic Parity

JUnit builds live-style Binance, OKX, and Kraken books, replays the corresponding envelopes, and compares final sequence, quality, best bid, best ask, bids, and asks. The live V21 runner performs the same top-10 parity check at the end of every replay-safe smoke run.

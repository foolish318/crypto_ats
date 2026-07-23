# Recorder And Replay Journal Module

![Recorder and replay journal](recorder-replay.svg)

PNG fallback: [recorder-replay.png](recorder-replay.png)

V24 turns raw capture into a bounded, segmented, checksummed journal while preserving legacy JSONL replay.

## Envelope And Frame

`RawEnvelope` contains application version, record type, generation, source/exchange/symbol, epoch and monotonic receive clocks, payload, and lifecycle detail. It records `CONNECT`, `DISCONNECT`, `RECOVERY`, `REST_SNAPSHOT`, and `WS_MESSAGE`. Binance REST snapshots are recorded before and after apply; OKX/Kraken callback order is retained.

Each journal segment starts with a versioned metadata header. Record frames include segment/frame indexes and SHA-256 checksum. The sidecar index records segment range, record count, byte count, and open/close times. `RawReplayCursor` exposes replay progress.

## Rotation And Durability

Defaults are 128 MiB or 15 minutes per segment, 24-hour retention, 64 MiB minimum free disk, flush every 256 records, and fsync every 4096 records plus rotation/close. The acknowledged-but-not-fsynced interval is the explicit crash-loss window.

`AsyncRawRecorder` drains its bounded queue on normal close. Queue depth, maximum depth, write lag, drops, current segment, disk usage, and replay safety are available in summary output. Any dropped record or writer failure makes the journal replay-unsafe.

## Streaming Replay

`RawReplayProcessor` reads segments line by line into `IncrementalRawReplayProcessor`; memory does not scale with total file size. It validates version, segment order, checksums, unsafe markers, and incomplete tails. Deterministic tests compare final sequence, quality, best bid/ask, and retained depth after segmented replay.
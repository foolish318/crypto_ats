# Recorder And Replay

![Recorder and replay](recorder-replay.svg)

`AsyncRawRecorder` uses a bounded 65,536-record queue. `RawJournalWriter` writes connection lifecycle, generation, REST snapshot, WebSocket payload, disconnect, and recovery records into time/size-rotated segments with metadata headers, SHA-256 frame checksums, and an index.

Queue overflow, disk-space failure, or writer error sets `replaySafe=false` with first-drop details. `RawReplayProcessor` streams records into `IncrementalRawReplayProcessor`; it does not load the whole file. Replay detects checksum damage and safely identifies an incomplete crash tail. Final source sequence, quality, best bid/ask, and depth must match live processing.
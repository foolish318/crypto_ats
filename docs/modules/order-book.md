# Venue-Local Order Book

![Venue-local order book](order-book.svg)

Each `LiveBookSession` owns one mutable book and one sequential writer. `BinanceLocalOrderBookBuilder`, `OkxLocalOrderBookBuilder`, and `KrakenLocalOrderBookBuilder` implement venue sequence, snapshot, and checksum rules over `MutableDecimalOrderBook`.

Snapshots are immutable and depth bounded for publication. Binance bootstrap buffering is bounded by 50,000 messages and 64 MiB; overflow fails closed and triggers recovery rather than silently losing continuity.
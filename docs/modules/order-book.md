# Venue-Local Order Book

![Venue-local order book](order-book.svg)

Each `LiveBookSession` owns one mutable book and one writer. Snapshot replacement and strictly validated deltas update exact-decimal levels. Every accepted mutation increments process-local `bookVersion`; reset clears depth but does not move that version backwards.

The mutable maps remain private. Publication copies configured top-N depth into `BookSnapshot` and `ImmutableOrderBookView`, which expose best bid/ask, spread, mid, depth-at-price, version, health, timestamps, and age. `BookMath` adds only depth arithmetic: available quantity, sweep price, and executable VWAP.
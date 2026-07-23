# Consolidated Cross-Exchange View

![Consolidated view](cross-exchange-view.svg)

`CrossExchangeBookView` groups venue snapshots by `canonicalInstrumentId`, not venue symbol. It retains state, generation, sequence, event/receive times, age, best prices, and immutable depth per venue.

Only current, live, fresh venues participate. `ConsolidatedBookSnapshot` reports best bid/venue, best ask/venue, spread, locked/crossed state, usable venue count, watermark, and coherence. Availability events remove a venue immediately.
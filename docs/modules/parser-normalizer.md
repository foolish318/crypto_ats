# Parser And Normalizer

![Parser and normalizer](parser-normalizer.svg)

Venue builders parse public JSON into exact-decimal price/quantity levels and apply venue-specific snapshot/update semantics. Transport payloads remain inside the source pipeline.

The engine boundary is `AcceptedLocalBookEvent`, containing source, venue symbol, canonical instrument, generation, sequence, clocks, quality, and immutable depth. Consumers never parse venue JSON.
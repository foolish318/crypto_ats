# Multi-Venue View

![Consolidated view](cross-exchange-view.svg)

`MultiVenueBookView` groups the latest immutable views by canonical `InstrumentId`. Every venue retains its own symbol, bookVersion, stream epoch, health, age, exchange time, receive time, and depth.

The view does not claim simultaneity and does not calculate arbitrage. Existing `CrossExchangeBookView` continues to provide monitored NBBO/coherence metrics, while the stable strategy API exposes source-preserving venue views.
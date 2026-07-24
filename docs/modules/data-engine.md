# Data Engine

![Data engine](data-engine.svg)

`MarketDataEngine` accepts both validated books and canonical public trades. Books and trades update their fenced cache before event publication. Book availability immediately invalidates the engine cache and changes strategy-view health.

`DefaultStrategyMarketDataPort` is a core deterministic listener. It stores an immutable view before emitting `BookUpdateNotification`, so version N is readable during the version N callback. Listener exceptions are isolated and counted.
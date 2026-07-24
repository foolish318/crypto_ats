package com.example.hft.marketdata.api;

import com.example.hft.marketdata.model.InstrumentId;
import com.example.hft.marketdata.model.PublicTrade;
import com.example.hft.marketdata.model.Venue;
import java.util.Optional;

public interface StrategyMarketDataPort {
    Optional<OrderBookView> getBook(Venue venue, InstrumentId instrument);

    MultiVenueBookView getBooks(InstrumentId instrument);

    Optional<PublicTrade> latestTrade(Venue venue, InstrumentId instrument);

    void subscribe(StrategyMarketDataListener listener);

    void unsubscribe(StrategyMarketDataListener listener);
}
package com.example.hft.marketdata.api;

import com.example.hft.marketdata.model.BookStatusChange;
import com.example.hft.marketdata.model.PublicTrade;

public interface StrategyMarketDataListener {
    default void onBookUpdated(BookUpdateNotification notification) {
    }

    default void onTrade(PublicTrade trade) {
    }

    default void onBookStatusChanged(BookStatusChange status) {
    }
}
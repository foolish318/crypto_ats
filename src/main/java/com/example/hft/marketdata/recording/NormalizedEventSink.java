package com.example.hft.marketdata.recording;

import com.example.hft.marketdata.model.BookSnapshot;
import com.example.hft.marketdata.model.BookStatusChange;
import com.example.hft.marketdata.model.PublicTrade;

public interface NormalizedEventSink {
    boolean recordBook(BookSnapshot book);

    boolean recordTrade(PublicTrade trade);

    boolean recordStatus(BookStatusChange status);

    static NormalizedEventSink noop() {
        return new NormalizedEventSink() {
            @Override
            public boolean recordBook(BookSnapshot book) {
                return true;
            }

            @Override
            public boolean recordTrade(PublicTrade trade) {
                return true;
            }

            @Override
            public boolean recordStatus(BookStatusChange status) {
                return true;
            }
        };
    }
}
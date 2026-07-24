package com.example.hft.marketdata.model;

import java.util.List;

public record BookDelta(
        MarketEventHeader header,
        List<BookLevelChange> changes
) {
    public BookDelta {
        if (header == null) {
            throw new IllegalArgumentException("book delta header is required");
        }
        changes = List.copyOf(changes);
    }
}
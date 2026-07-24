package com.example.hft.marketdata.recording;

import com.example.hft.marketdata.model.BookSnapshot;
import com.example.hft.marketdata.model.BookStatusChange;
import com.example.hft.marketdata.model.PublicTrade;

public record NormalizedEventRecord(
        int schemaVersion,
        long ordinal,
        NormalizedEventType type,
        BookSnapshot book,
        PublicTrade trade,
        BookStatusChange status
) {
    public NormalizedEventRecord {
        if (schemaVersion <= 0 || ordinal <= 0L || type == null) {
            throw new IllegalArgumentException("normalized event version, ordinal, and type are required");
        }
        int values = (book == null ? 0 : 1) + (trade == null ? 0 : 1) + (status == null ? 0 : 1);
        if (values != 1
                || type == NormalizedEventType.BOOK && book == null
                || type == NormalizedEventType.TRADE && trade == null
                || type == NormalizedEventType.BOOK_STATUS && status == null) {
            throw new IllegalArgumentException("normalized event payload must match its type");
        }
    }

    public static NormalizedEventRecord book(long ordinal, BookSnapshot book) {
        return new NormalizedEventRecord(1, ordinal, NormalizedEventType.BOOK, book, null, null);
    }

    public static NormalizedEventRecord trade(long ordinal, PublicTrade trade) {
        return new NormalizedEventRecord(1, ordinal, NormalizedEventType.TRADE, null, trade, null);
    }

    public static NormalizedEventRecord status(long ordinal, BookStatusChange status) {
        return new NormalizedEventRecord(1, ordinal, NormalizedEventType.BOOK_STATUS, null, null, status);
    }
}
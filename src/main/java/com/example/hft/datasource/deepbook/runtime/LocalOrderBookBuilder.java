package com.example.hft.datasource.deepbook.runtime;

import com.example.hft.datasource.book.BookQuality;
import com.example.hft.datasource.deepbook.DeepBookSourceDefinition;


public interface LocalOrderBookBuilder {
    DeepBookSourceDefinition source();

    BookUpdateResult loadSnapshot(String payload, long receivedEpochMillis);

    BookUpdateResult onMessage(String payload, long receivedEpochMillis);

    LocalBookSnapshot snapshot(int levels);

    BookQuality quality();

    long acceptedMessages();

    long rejectedMessages();

    void reset();
}

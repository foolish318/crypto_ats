package com.example.hft.datasource.deepbook.runtime;

import java.util.Map;
import java.util.Optional;


public record RawReplayResult(
        Map<String, LocalBookSnapshot> finalBooks,
        long appliedRecords,
        long ignoredRecords
) {
    public RawReplayResult {
        finalBooks = Map.copyOf(finalBooks);
    }

    public Optional<LocalBookSnapshot> book(String sourceId) {
        return Optional.ofNullable(finalBooks.get(sourceId));
    }
}

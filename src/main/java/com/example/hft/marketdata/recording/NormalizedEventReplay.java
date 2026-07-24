package com.example.hft.marketdata.recording;

import com.example.hft.marketdata.api.DefaultStrategyMarketDataPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class NormalizedEventReplay {
    private final ObjectMapper mapper;

    public NormalizedEventReplay(ObjectMapper mapper) {
        if (mapper == null) {
            throw new IllegalArgumentException("mapper is required");
        }
        this.mapper = mapper;
    }

    public NormalizedReplayResult replay(
            Path path,
            DefaultStrategyMarketDataPort target
    ) throws Exception {
        long records = 0L;
        long lastOrdinal = 0L;
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                NormalizedEventRecord record = mapper.readValue(line, NormalizedEventRecord.class);
                if (record.ordinal() != lastOrdinal + 1L) {
                    throw new IllegalStateException(
                            "normalized replay ordinal gap expected=" + (lastOrdinal + 1L)
                                    + " actual=" + record.ordinal());
                }
                switch (record.type()) {
                    case BOOK -> target.applyRecordedBook(record.book());
                    case TRADE -> target.applyRecordedTrade(record.trade());
                    case BOOK_STATUS -> target.applyRecordedStatus(record.status());
                }
                lastOrdinal = record.ordinal();
                records++;
            }
        }
        return new NormalizedReplayResult(records, lastOrdinal);
    }
}
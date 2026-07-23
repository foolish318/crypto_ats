package com.example.hft.datasource.deepbook.runtime;

import com.example.hft.datasource.book.BookQuality;
import com.example.hft.datasource.deepbook.DeepBookSourceDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


public final class RawReplayProcessor {
    private final Map<String, DeepBookSourceDefinition> sources;
    private final int snapshotLevels;
    private final ObjectMapper mapper;

    public RawReplayProcessor(
            List<DeepBookSourceDefinition> sources,
            int snapshotLevels,
            ObjectMapper mapper
    ) {
        this.sources = new HashMap<>();
        for (DeepBookSourceDefinition source : sources) {
            this.sources.put(source.id(), source);
        }
        this.snapshotLevels = snapshotLevels;
        this.mapper = mapper;
    }

    public RawReplayResult replay(Path path) throws Exception {
        try (var lines = Files.lines(path)) {
            List<RawEnvelope> records = lines
                    .filter(line -> !line.isBlank())
                    .map(this::read)
                    .toList();
            return replay(records);
        }
    }

    public RawReplayResult replay(List<RawEnvelope> records) {
        Map<String, ReplayState> states = new LinkedHashMap<>();
        long applied = 0L;
        long ignored = 0L;

        for (RawEnvelope record : records) {
            if (record.detail().startsWith("REPLAY_UNSAFE")) {
                throw new IllegalStateException(record.detail());
            }
            DeepBookSourceDefinition source = sources.get(record.sourceId());
            if (source == null) {
                ignored++;
                continue;
            }
            ReplayState state = states.get(record.sourceId());
            if (record.recordType() == RawRecordType.CONNECT) {
                if (state == null || state.generation != record.generation()) {
                    state = new ReplayState(
                            record.generation(),
                            LocalOrderBookBuilderFactory.create(source)
                    );
                    states.put(record.sourceId(), state);
                }
                state.active = true;
                continue;
            }
            if (state == null || state.generation != record.generation()) {
                ignored++;
                continue;
            }
            if (record.recordType() == RawRecordType.DISCONNECT
                    || record.recordType() == RawRecordType.RECOVERY) {
                state.active = false;
                continue;
            }
            if (!state.active) {
                ignored++;
                continue;
            }

            if (record.recordType() == RawRecordType.WS_MESSAGE
                    && "BINANCE_US".equals(source.exchange())
                    && state.builder.quality() == BookQuality.EMPTY) {
                state.pendingBinance.add(record);
                continue;
            }

            BookUpdateResult result = apply(record, state.builder);
            if (result == null) {
                ignored++;
                continue;
            }
            requireReplayable(record, result);
            applied++;

            if (record.recordType() == RawRecordType.REST_SNAPSHOT
                    && record.detail().startsWith("BEFORE_APPLY")) {
                for (RawEnvelope pending : state.pendingBinance) {
                    BookUpdateResult pendingResult = apply(pending, state.builder);
                    requireReplayable(pending, pendingResult);
                    applied++;
                }
                state.pendingBinance.clear();
            }
        }

        Map<String, LocalBookSnapshot> books = new LinkedHashMap<>();
        for (Map.Entry<String, ReplayState> entry : states.entrySet()) {
            books.put(entry.getKey(), entry.getValue().builder.snapshot(snapshotLevels));
        }
        return new RawReplayResult(books, applied, ignored);
    }

    private static BookUpdateResult apply(
            RawEnvelope record,
            LocalOrderBookBuilder builder
    ) {
        if (record.recordType() == RawRecordType.REST_SNAPSHOT
                && record.detail().startsWith("BEFORE_APPLY")) {
            return builder.loadSnapshot(record.payload(), record.receivedEpochMillis());
        }
        if (record.recordType() == RawRecordType.WS_MESSAGE) {
            return builder.onMessage(record.payload(), record.receivedEpochMillis());
        }
        return null;
    }

    private static void requireReplayable(
            RawEnvelope record,
            BookUpdateResult result
    ) {
        if (result == null) {
            throw new IllegalStateException("missing replay result for " + record.sourceId());
        }
        if (result.status() == BookUpdateStatus.PARSE_FAILED) {
            throw new IllegalStateException(
                    "replay parse failed for " + record.sourceId() + ": " + result.detail());
        }
    }

    private RawEnvelope read(String line) {
        try {
            return mapper.readValue(line, RawEnvelope.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid raw replay line", e);
        }
    }

    private static final class ReplayState {
        private final long generation;
        private final LocalOrderBookBuilder builder;
        private final List<RawEnvelope> pendingBinance = new ArrayList<>();
        private boolean active = true;

        private ReplayState(
                long generation,
                LocalOrderBookBuilder builder
        ) {
            this.generation = generation;
            this.builder = builder;
        }
    }
}

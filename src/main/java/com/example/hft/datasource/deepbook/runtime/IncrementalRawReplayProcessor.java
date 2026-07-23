package com.example.hft.datasource.deepbook.runtime;

import com.example.hft.datasource.book.BookQuality;
import com.example.hft.datasource.deepbook.DeepBookSourceDefinition;
import com.example.hft.datasource.instrument.Instrument;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


public final class IncrementalRawReplayProcessor {
    private final Map<String, DeepBookSourceDefinition> sources = new HashMap<>();
    private final Map<String, Instrument> instruments;
    private final Map<String, ReplayState> states = new LinkedHashMap<>();
    private final int snapshotLevels;
    private final ObjectMapper mapper;
    private long applied;
    private long ignored;

    public IncrementalRawReplayProcessor(
            List<DeepBookSourceDefinition> sources,
            int snapshotLevels,
            ObjectMapper mapper
    ) {
        this(sources, snapshotLevels, mapper, Map.of());
    }

    public IncrementalRawReplayProcessor(
            List<DeepBookSourceDefinition> sources,
            int snapshotLevels,
            ObjectMapper mapper,
            Map<String, Instrument> instruments
    ) {
        for (DeepBookSourceDefinition source : sources) {
            this.sources.put(source.id(), source);
        }
        this.snapshotLevels = snapshotLevels;
        this.mapper = mapper;
        this.instruments = Map.copyOf(instruments);
    }

    public void accept(RawEnvelope record) {
        if (record.detail().startsWith("REPLAY_UNSAFE")) {
            throw new IllegalStateException(record.detail());
        }
        DeepBookSourceDefinition source = sources.get(record.sourceId());
        if (source == null) {
            ignored++;
            return;
        }
        ReplayState state = states.get(record.sourceId());
        if (record.recordType() == RawRecordType.CONNECT) {
            if (state == null || state.generation != record.generation()) {
                state = new ReplayState(
                        record.generation(),
                        LocalOrderBookBuilderFactory.create(source, instruments.get(source.id())),
                        new VenueProtocolMessageClassifier(source, mapper)
                );
                states.put(record.sourceId(), state);
            }
            state.active = true;
            return;
        }
        if (state == null || state.generation != record.generation()) {
            ignored++;
            return;
        }
        if (record.recordType() == RawRecordType.DISCONNECT
                || record.recordType() == RawRecordType.RECOVERY) {
            state.active = false;
            return;
        }
        if (!state.active) {
            ignored++;
            return;
        }

        if (record.recordType() == RawRecordType.WS_MESSAGE) {
            if (record.detail().startsWith("CONTROL ")) {
                ignored++;
                return;
            }
            ProtocolMessageDecision decision = state.classifier.classify(record.payload());
            if (decision.fatal()) {
                state.active = false;
                ignored++;
                return;
            }
            if (!decision.bookData()) {
                ignored++;
                return;
            }
            if ("BINANCE_US".equals(source.exchange())
                    && state.builder.quality() == BookQuality.EMPTY) {
                state.pendingBinance.add(record);
                return;
            }
        }

        BookUpdateResult result = apply(record, state.builder);
        if (result == null) {
            ignored++;
            return;
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

    public RawReplayResult result() {
        Map<String, LocalBookSnapshot> books = new LinkedHashMap<>();
        for (Map.Entry<String, ReplayState> entry : states.entrySet()) {
            books.put(entry.getKey(), entry.getValue().builder.snapshot(snapshotLevels));
        }
        return new RawReplayResult(books, applied, ignored);
    }

    private static BookUpdateResult apply(RawEnvelope record, LocalOrderBookBuilder builder) {
        if (record.recordType() == RawRecordType.REST_SNAPSHOT
                && record.detail().startsWith("BEFORE_APPLY")) {
            return builder.loadSnapshot(record.payload(), record.receivedEpochMillis());
        }
        if (record.recordType() == RawRecordType.WS_MESSAGE) {
            return builder.onMessage(record.payload(), record.receivedEpochMillis());
        }
        return null;
    }

    private static void requireReplayable(RawEnvelope record, BookUpdateResult result) {
        if (result == null) {
            throw new IllegalStateException("missing replay result for " + record.sourceId());
        }
        if (result.status() == BookUpdateStatus.PARSE_FAILED) {
            throw new IllegalStateException(
                    "replay parse failed for " + record.sourceId() + ": " + result.detail());
        }
    }

    private static final class ReplayState {
        private final long generation;
        private final LocalOrderBookBuilder builder;
        private final VenueProtocolMessageClassifier classifier;
        private final List<RawEnvelope> pendingBinance = new ArrayList<>();
        private boolean active = true;

        private ReplayState(
                long generation,
                LocalOrderBookBuilder builder,
                VenueProtocolMessageClassifier classifier
        ) {
            this.generation = generation;
            this.builder = builder;
            this.classifier = classifier;
        }
    }
}
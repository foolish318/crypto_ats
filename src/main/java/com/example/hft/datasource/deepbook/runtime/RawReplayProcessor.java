package com.example.hft.datasource.deepbook.runtime;

import com.example.hft.datasource.deepbook.DeepBookSourceDefinition;
import com.example.hft.datasource.instrument.Instrument;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;


public final class RawReplayProcessor {
    private final List<DeepBookSourceDefinition> sources;
    private final int snapshotLevels;
    private final ObjectMapper mapper;
    private final Map<String, Instrument> instruments;

    public RawReplayProcessor(
            List<DeepBookSourceDefinition> sources,
            int snapshotLevels,
            ObjectMapper mapper
    ) {
        this(sources, snapshotLevels, mapper, Map.of());
    }

    public RawReplayProcessor(
            List<DeepBookSourceDefinition> sources,
            int snapshotLevels,
            ObjectMapper mapper,
            Map<String, Instrument> instruments
    ) {
        this.sources = List.copyOf(sources);
        this.snapshotLevels = snapshotLevels;
        this.mapper = mapper;
        this.instruments = Map.copyOf(instruments);
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
        IncrementalRawReplayProcessor processor = new IncrementalRawReplayProcessor(
                sources,
                snapshotLevels,
                mapper,
                instruments
        );
        records.forEach(processor::accept);
        return processor.result();
    }

    private RawEnvelope read(String line) {
        try {
            return mapper.readValue(line, RawEnvelope.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid raw replay line", e);
        }
    }
}
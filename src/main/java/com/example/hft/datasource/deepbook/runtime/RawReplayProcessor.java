package com.example.hft.datasource.deepbook.runtime;

import com.example.hft.datasource.deepbook.DeepBookSourceDefinition;
import com.example.hft.datasource.instrument.Instrument;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;


public final class RawReplayProcessor {
    private final List<DeepBookSourceDefinition> sources;
    private final int snapshotLevels;
    private final ObjectMapper mapper;
    private final Map<String, Instrument> instruments;
    private volatile RawReplayCursor cursor = new RawReplayCursor(0, 0L, 0L, 0L);

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
        IncrementalRawReplayProcessor processor = new IncrementalRawReplayProcessor(
                sources,
                snapshotLevels,
                mapper,
                instruments
        );
        List<Path> segments = discoverSegments(path);
        for (int index = 0; index < segments.size(); index++) {
            replaySegment(segments.get(index), index, processor);
        }
        RawReplayResult result = processor.result();
        cursor = new RawReplayCursor(
                Math.max(0, segments.size() - 1),
                cursor.frameIndex(),
                result.appliedRecords(),
                result.ignoredRecords()
        );
        return result;
    }

    public RawReplayResult replay(List<RawEnvelope> records) {
        IncrementalRawReplayProcessor processor = new IncrementalRawReplayProcessor(
                sources,
                snapshotLevels,
                mapper,
                instruments
        );
        records.forEach(processor::accept);
        RawReplayResult result = processor.result();
        cursor = new RawReplayCursor(
                0,
                records.size(),
                result.appliedRecords(),
                result.ignoredRecords()
        );
        return result;
    }

    public RawReplayCursor cursor() {
        return cursor;
    }

    private void replaySegment(
            Path path,
            int expectedSegmentIndex,
            IncrementalRawReplayProcessor processor
    ) throws Exception {
        boolean newlineTerminated = newlineTerminated(path);
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String current = reader.readLine();
            long lineNumber = 1L;
            while (current != null) {
                String next = reader.readLine();
                boolean last = next == null;
                if (!current.isBlank()) {
                    try {
                        processLine(current, expectedSegmentIndex, processor);
                    } catch (Exception error) {
                        if (error instanceof IllegalStateException stateError
                                && stateError.getMessage() != null
                                && stateError.getMessage().startsWith("REPLAY_UNSAFE")) {
                            throw stateError;
                        }
                        if (last && !newlineTerminated) {
                            throw new IllegalStateException(
                                    "truncated raw journal tail at " + path
                                            + ":" + lineNumber,
                                    error
                            );
                        }
                        throw new IllegalArgumentException(
                                "invalid raw replay line " + path + ":" + lineNumber,
                                error
                        );
                    }
                }
                current = next;
                lineNumber++;
            }
        }
    }

    private void processLine(
            String line,
            int expectedSegmentIndex,
            IncrementalRawReplayProcessor processor
    ) throws Exception {
        JsonNode root = mapper.readTree(line);
        if (!root.has("frameType")) {
            processor.accept(mapper.treeToValue(root, RawEnvelope.class));
            cursor = new RawReplayCursor(
                    expectedSegmentIndex,
                    cursor.frameIndex() + 1L,
                    processor.appliedRecords(),
                    processor.ignoredRecords()
            );
            return;
        }
        RawJournalFrame frame = mapper.treeToValue(root, RawJournalFrame.class);
        if (!RawJournalWriter.JOURNAL_VERSION.equals(frame.journalVersion())) {
            throw new IllegalStateException("unsupported journal version " + frame.journalVersion());
        }
        if (frame.segmentIndex() != expectedSegmentIndex) {
            throw new IllegalStateException(
                    "journal segment order mismatch expected=" + expectedSegmentIndex
                            + " actual=" + frame.segmentIndex());
        }
        cursor = new RawReplayCursor(
                frame.segmentIndex(),
                frame.frameIndex(),
                processor.appliedRecords(),
                processor.ignoredRecords()
        );
        if (frame.frameType() == RawJournalFrameType.HEADER) {
            String expected = RawJournalChecksum.text(frame.sourceMetadata());
            if (!expected.equals(frame.checksum())) {
                throw new IllegalStateException("journal header checksum mismatch");
            }
            return;
        }
        if (frame.record() == null) {
            throw new IllegalStateException("journal record frame has no record");
        }
        String expected = RawJournalChecksum.record(mapper, frame.record());
        if (!expected.equals(frame.checksum())) {
            throw new IllegalStateException(
                    "journal record checksum mismatch at frame " + frame.frameIndex());
        }
        processor.accept(frame.record());
    }

    static List<Path> discoverSegments(Path base) throws Exception {
        List<Path> result = new ArrayList<>();
        if (Files.exists(base)) {
            result.add(base);
        }
        Path parent = base.toAbsolutePath().getParent();
        if (parent == null || !Files.exists(parent)) {
            return result;
        }
        String file = base.getFileName().toString();
        int extension = file.lastIndexOf('.');
        String stem = extension < 0 ? file : file.substring(0, extension);
        String suffix = extension < 0 ? "" : file.substring(extension);
        String prefix = stem + ".segment-";
        try (var paths = Files.list(parent)) {
            paths.filter(path -> {
                        String name = path.getFileName().toString();
                        return name.startsWith(prefix) && name.endsWith(suffix);
                    })
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .forEach(result::add);
        }
        return List.copyOf(result);
    }

    private static boolean newlineTerminated(Path path) throws Exception {
        long size = Files.size(path);
        if (size == 0L) {
            return true;
        }
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
            ByteBuffer last = ByteBuffer.allocate(1);
            channel.position(size - 1L);
            channel.read(last);
            last.flip();
            return last.get() == (byte) '\n';
        }
    }
}

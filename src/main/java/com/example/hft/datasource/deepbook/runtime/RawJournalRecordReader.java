package com.example.hft.datasource.deepbook.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;


public final class RawJournalRecordReader {
    private RawJournalRecordReader() {
    }

    public static List<RawEnvelope> readAll(Path path, ObjectMapper mapper) throws Exception {
        List<RawEnvelope> records = new ArrayList<>();
        forEach(path, mapper, records::add);
        return List.copyOf(records);
    }

    public static void forEach(
            Path path,
            ObjectMapper mapper,
            Consumer<RawEnvelope> consumer
    ) throws Exception {
        for (Path segment : RawReplayProcessor.discoverSegments(path)) {
            try (var reader = Files.newBufferedReader(segment, StandardCharsets.UTF_8)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) {
                        continue;
                    }
                    JsonNode root = mapper.readTree(line);
                    if (!root.has("frameType")) {
                        consumer.accept(mapper.treeToValue(root, RawEnvelope.class));
                        continue;
                    }
                    RawJournalFrame frame = mapper.treeToValue(root, RawJournalFrame.class);
                    if (frame.frameType() == RawJournalFrameType.HEADER) {
                        continue;
                    }
                    if (frame.record() == null
                            || !RawJournalChecksum.record(mapper, frame.record())
                            .equals(frame.checksum())) {
                        throw new IllegalStateException(
                                "journal checksum mismatch at frame " + frame.frameIndex());
                    }
                    consumer.accept(frame.record());
                }
            }
        }
    }
}

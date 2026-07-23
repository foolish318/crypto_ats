package com.example.hft.datasource.deepbook.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.hft.datasource.DataSourceModuleVersion;
import com.example.hft.datasource.deepbook.DeepBookSourceCatalog;
import com.example.hft.datasource.deepbook.DeepBookSourceDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;


class RawJournalReplayTest {
    @TempDir
    Path tempDir;

    private final ObjectMapper mapper = new ObjectMapper();
    private final DeepBookSourceDefinition source = DeepBookSourceCatalog.okx("BTC-USDT");

    @Test
    void segmentedJournalReplaysInOrderWithBookParity() throws Exception {
        Path path = tempDir.resolve("raw.jsonl");
        RawJournalConfig config = config(900L);
        AsyncRawRecorder recorder = new AsyncRawRecorder(path, mapper, 16, config);
        for (RawEnvelope envelope : validBookRecords()) {
            assertTrue(recorder.record(envelope));
        }
        recorder.close();

        assertTrue(RawReplayProcessor.discoverSegments(path).size() > 1);
        RawReplayProcessor replay = new RawReplayProcessor(List.of(source), 10, mapper);
        LocalBookSnapshot book = replay.replay(path).book(source.id()).orElseThrow();

        assertEquals(11L, book.sequence());
        assertEquals("1.1", book.bestBid().quantity().toPlainString());
        assertTrue(replay.cursor().segmentIndex() > 0);
        assertTrue(Files.exists(Path.of(path + ".index")));
    }

    @Test
    void checksumCorruptionIsDetected() throws Exception {
        Path path = tempDir.resolve("corrupt.jsonl");
        AsyncRawRecorder recorder = new AsyncRawRecorder(path, mapper, 16, config(1_000_000L));
        for (RawEnvelope envelope : validBookRecords()) {
            recorder.record(envelope);
        }
        recorder.close();
        String content = Files.readString(path);
        Files.writeString(path, content.replaceFirst("100\\.0", "999.0"));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> new RawReplayProcessor(List.of(source), 10, mapper).replay(path)
        );
        assertTrue(rootMessage(error).contains("checksum mismatch"));
    }

    @Test
    void truncatedTailProducesExplicitFailure() throws Exception {
        Path path = tempDir.resolve("truncated.jsonl");
        AsyncRawRecorder recorder = new AsyncRawRecorder(path, mapper, 16, config(1_000_000L));
        recorder.record(validBookRecords().get(0));
        recorder.close();
        Files.writeString(
                path,
                "{\"frameType\":",
                StandardCharsets.UTF_8,
                StandardOpenOption.APPEND
        );

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> new RawReplayProcessor(List.of(source), 10, mapper).replay(path)
        );
        assertTrue(error.getMessage().contains("truncated raw journal tail"));
    }

    @Test
    void actualQueueOverflowMarksJournalReplayUnsafe() throws Exception {
        Path path = tempDir.resolve("overflow.jsonl");
        AsyncRawRecorder recorder = new AsyncRawRecorder(path, mapper, 1, config(1_000_000L));
        RawEnvelope envelope = validBookRecords().get(0);
        boolean dropped = false;
        for (int i = 0; i < 10_000; i++) {
            dropped |= !recorder.record(envelope);
        }
        recorder.close();

        assertTrue(dropped);
        assertFalse(recorder.summary().replaySafe());
        assertTrue(recorder.summary().droppedRecords() > 0L);
        assertThrows(
                IllegalStateException.class,
                () -> new RawReplayProcessor(List.of(source), 10, mapper).replay(path)
        );
    }

    @Test
    void legacyLargeFileIsProcessedAsAStream() throws Exception {
        Path path = tempDir.resolve("large-legacy.jsonl");
        RawEnvelope control = envelope(
                RawRecordType.CONNECT,
                "",
                "CONNECTED",
                1L
        );
        try (var writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            for (int i = 0; i < 25_000; i++) {
                writer.write(mapper.writeValueAsString(control));
                writer.newLine();
            }
        }

        RawReplayProcessor replay = new RawReplayProcessor(List.of(source), 1, mapper);
        replay.replay(path);

        assertEquals(25_000L, replay.cursor().frameIndex());
    }

    private List<RawEnvelope> validBookRecords() {
        return List.of(
                envelope(RawRecordType.CONNECT, "", "CONNECTED", 1L),
                envelope(
                        RawRecordType.WS_MESSAGE,
                        "{\"arg\":{\"channel\":\"books\",\"instId\":\"BTC-USDT\"},"
                                + "\"action\":\"snapshot\",\"data\":[{"
                                + "\"bids\":[[\"100.0\",\"1.0\",\"0\",\"1\"]],"
                                + "\"asks\":[[\"101.0\",\"2.0\",\"0\",\"1\"]],"
                                + "\"ts\":\"1000\",\"seqId\":10,\"prevSeqId\":-1}]}",
                        "",
                        1L
                ),
                envelope(
                        RawRecordType.WS_MESSAGE,
                        "{\"arg\":{\"channel\":\"books\",\"instId\":\"BTC-USDT\"},"
                                + "\"action\":\"update\",\"data\":[{"
                                + "\"bids\":[[\"100.0\",\"1.1\",\"0\",\"1\"]],"
                                + "\"asks\":[],\"ts\":\"2000\",\"seqId\":11,"
                                + "\"prevSeqId\":10}]}",
                        "",
                        1L
                )
        );
    }

    private RawEnvelope envelope(
            RawRecordType type,
            String payload,
            String detail,
            long generation
    ) {
        return new RawEnvelope(
                DataSourceModuleVersion.VERSION,
                type,
                generation,
                source.id(),
                source.exchange(),
                source.symbol(),
                3_000L,
                System.nanoTime(),
                payload,
                detail
        );
    }

    private static RawJournalConfig config(long maxSegmentBytes) {
        return new RawJournalConfig(
                maxSegmentBytes,
                Duration.ofHours(1),
                Duration.ofHours(1),
                0L,
                1,
                1,
                "test-source"
        );
    }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? "" : current.getMessage();
    }
}

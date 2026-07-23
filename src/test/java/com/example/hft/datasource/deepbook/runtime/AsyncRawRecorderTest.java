package com.example.hft.datasource.deepbook.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.hft.datasource.DataSourceModuleVersion;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;


class AsyncRawRecorderTest {
    @TempDir
    Path tempDir;

    @Test
    void normalShutdownPersistsEveryAcceptedRecord() throws Exception {
        Path output = tempDir.resolve("complete.jsonl");
        ObjectMapper mapper = new ObjectMapper();
        AsyncRawRecorder recorder = new AsyncRawRecorder(output, mapper, 128);
        RawEnvelope record = new RawEnvelope(
                DataSourceModuleVersion.VERSION,
                RawRecordType.CONNECT,
                1L,
                "okx-BTC-USDT",
                "OKX",
                "BTC-USDT",
                1_000L,
                2_000L,
                "",
                "CONNECTED"
        );
        for (int index = 0; index < 100; index++) {
            assertTrue(recorder.record(record));
        }

        recorder.close();

        assertEquals(100L, recorder.summary().recordedRecords());
        assertTrue(recorder.summary().replaySafe());
        assertEquals(100, RawJournalRecordReader.readAll(output, mapper).size());
    }

    @Test
    void explicitProcessingDropMarksReplayUnsafeAndWritesMarker() throws Exception {
        Path output = tempDir.resolve("raw.jsonl");
        AsyncRawRecorder recorder = new AsyncRawRecorder(output, new ObjectMapper(), 8);
        RawEnvelope dropped = new RawEnvelope(
                DataSourceModuleVersion.VERSION,
                RawRecordType.WS_MESSAGE,
                1L,
                "okx-BTC-USDT",
                "OKX",
                "BTC-USDT",
                1_000L,
                2_000L,
                "{}",
                "PROCESSING_QUEUE_FULL"
        );

        recorder.markReplayUnsafe(dropped, "processing queue full");
        recorder.close();

        RawRecorderSummary summary = recorder.summary();
        assertFalse(summary.replaySafe());
        assertTrue(summary.firstDropReason().contains("processing queue full"));
        assertTrue(Files.readString(output).contains("REPLAY_UNSAFE"));
    }
}
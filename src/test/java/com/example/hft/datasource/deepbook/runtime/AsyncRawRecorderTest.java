package com.example.hft.datasource.deepbook.runtime;

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
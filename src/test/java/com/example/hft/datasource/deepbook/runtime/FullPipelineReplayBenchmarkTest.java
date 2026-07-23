package com.example.hft.datasource.deepbook.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.hft.datasource.DataSourceModuleVersion;
import com.example.hft.datasource.deepbook.DeepBookSourceCatalog;
import com.example.hft.datasource.deepbook.DeepBookSourceDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;


class FullPipelineReplayBenchmarkTest {
    @TempDir
    Path tempDir;

    @Test
    void deterministicFullPipelinePreservesReplayParity() throws Exception {
        DeepBookSourceDefinition source = DeepBookSourceCatalog.okx("BTC-USDT");
        long receivedNanos = 1_000_000L;
        List<RawEnvelope> records = List.of(
                envelope(source, RawRecordType.CONNECT, "", "CONNECTED", receivedNanos),
                envelope(
                        source,
                        RawRecordType.WS_MESSAGE,
                        "{\"event\":\"subscribe\",\"arg\":{\"channel\":\"books\","
                                + "\"instId\":\"BTC-USDT\"}}",
                        "",
                        receivedNanos + 1_000_000L
                ),
                envelope(
                        source,
                        RawRecordType.WS_MESSAGE,
                        "{\"arg\":{\"channel\":\"books\",\"instId\":\"BTC-USDT\"},"
                                + "\"action\":\"snapshot\",\"data\":[{"
                                + "\"bids\":[[\"100.0\",\"1.0\",\"0\",\"1\"]],"
                                + "\"asks\":[[\"101.0\",\"2.0\",\"0\",\"1\"]],"
                                + "\"ts\":\"1000\",\"seqId\":10,\"prevSeqId\":-1}]}",
                        "",
                        receivedNanos + 2_000_000L
                ),
                envelope(
                        source,
                        RawRecordType.WS_MESSAGE,
                        "{\"arg\":{\"channel\":\"books\",\"instId\":\"BTC-USDT\"},"
                                + "\"action\":\"update\",\"data\":[{"
                                + "\"bids\":[[\"100.0\",\"1.1\",\"0\",\"1\"]],"
                                + "\"asks\":[],\"ts\":\"2000\",\"seqId\":11,"
                                + "\"prevSeqId\":10}]}",
                        "",
                        receivedNanos + 3_000_000L
                )
        );

        FullPipelineBenchmarkResult result = new FullPipelineReplayBenchmark(
                List.of(source),
                records,
                new ObjectMapper()
        ).run(tempDir.resolve("benchmark-journal.jsonl"));

        assertTrue(result.replayParity());
        assertEquals(0L, result.droppedMessages());
        assertEquals(0L, result.rejectedMessages());
        assertEquals(1L, result.bootstrapLatency().samples());
        assertEquals(1L, result.incrementalLatency().samples());
        assertTrue(result.stageLatency().get("protocol").samples() >= 3L);
        assertTrue(result.stageLatency().get("bookMutation").samples() >= 2L);
        assertTrue(result.recorder().replaySafe());
    }

    private static RawEnvelope envelope(
            DeepBookSourceDefinition source,
            RawRecordType type,
            String payload,
            String detail,
            long receivedNanos
    ) {
        return new RawEnvelope(
                DataSourceModuleVersion.VERSION,
                type,
                1L,
                source.id(),
                source.exchange(),
                source.symbol(),
                3_000L,
                receivedNanos,
                payload,
                detail
        );
    }
}
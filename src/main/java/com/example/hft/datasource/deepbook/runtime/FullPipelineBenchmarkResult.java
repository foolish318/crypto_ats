package com.example.hft.datasource.deepbook.runtime;

import com.example.hft.datasource.engine.AsyncListenerSnapshot;
import java.util.List;
import java.util.Map;


public record FullPipelineBenchmarkResult(
        long records,
        double elapsedMillis,
        double throughputPerSecond,
        double allocationBytesPerMessage,
        long gcCount,
        long gcPauseMillis,
        Map<String, PipelineLatencyDistribution> stageLatency,
        PipelineLatencyDistribution bootstrapLatency,
        PipelineLatencyDistribution incrementalLatency,
        long rejectedMessages,
        long droppedMessages,
        boolean replayParity,
        RawRecorderSummary recorder,
        List<AsyncListenerSnapshot> asyncListeners,
        String javaVersion,
        String vmName,
        int availableProcessors,
        String jvmArguments
) {
    public FullPipelineBenchmarkResult {
        stageLatency = Map.copyOf(stageLatency);
        asyncListeners = List.copyOf(asyncListeners);
    }
}

package com.example.hft.app;

import com.example.hft.datasource.deepbook.DeepBookSourceCatalog;
import com.example.hft.datasource.deepbook.runtime.FullPipelineBenchmarkResult;
import com.example.hft.datasource.deepbook.runtime.FullPipelineReplayBenchmark;
import com.example.hft.datasource.deepbook.runtime.PipelineLatencyDistribution;
import com.example.hft.datasource.deepbook.runtime.RawEnvelope;
import com.example.hft.datasource.deepbook.runtime.RawJournalRecordReader;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;


public final class FullPipelineBenchmarkMain {
    private static final int DEFAULT_RUNS = 3;

    private FullPipelineBenchmarkMain() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            throw new IllegalArgumentException(
                    "usage: FullPipelineBenchmarkMain <raw.jsonl> [runs] [output-prefix]");
        }
        Path input = Path.of(args[0]);
        int runs = args.length > 1 ? positiveInt(args[1]) : DEFAULT_RUNS;
        String runId = Instant.now().toString().replace(":", "").replace(".", "");
        Path outputPrefix = args.length > 2
                ? Path.of(args[2])
                : Path.of("data/full-pipeline-" + runId);
        ObjectMapper mapper = new ObjectMapper();
        List<RawEnvelope> records = RawJournalRecordReader.readAll(input, mapper);
        FullPipelineReplayBenchmark benchmark = new FullPipelineReplayBenchmark(
                DeepBookSourceCatalog.defaultSources(),
                records,
                mapper
        );

        benchmark.run(Path.of(outputPrefix + "-warmup.jsonl"));
        List<FullPipelineBenchmarkResult> results = new ArrayList<>();
        for (int run = 1; run <= runs; run++) {
            FullPipelineBenchmarkResult result = benchmark.run(
                    Path.of(outputPrefix + "-run-" + run + ".jsonl")
            );
            results.add(result);
            PipelineLatencyDistribution e2e =
                    result.stageLatency().get("endToEndCorrected");
            System.out.printf(Locale.ROOT,
                    "FULL_PIPELINE run=%d records=%d throughput=%.0f/s "
                            + "p99=%.2fus p999=%.2fus alloc=%.1fB/msg "
                            + "gc=%d/%dms recorderLagP=%d asyncDrops=%d parity=%s%n",
                    run,
                    result.records(),
                    result.throughputPerSecond(),
                    e2e.p99Micros(),
                    e2e.p999Micros(),
                    result.allocationBytesPerMessage(),
                    result.gcCount(),
                    result.gcPauseMillis(),
                    result.recorder().maxWriteLagNanos(),
                    result.asyncListeners().stream()
                            .mapToLong(item -> item.droppedEvents()).sum(),
                    result.replayParity());
        }
        FullPipelineBenchmarkResult median = results.stream()
                .sorted(Comparator.comparingDouble(
                        FullPipelineBenchmarkResult::throughputPerSecond))
                .toList()
                .get(results.size() / 2);
        Path json = Path.of(outputPrefix + ".json");
        Path markdown = Path.of(outputPrefix + ".md");
        if (json.toAbsolutePath().getParent() != null) {
            Files.createDirectories(json.toAbsolutePath().getParent());
        }
        ObjectNode root = mapper.createObjectNode();
        root.put("input", input.toString());
        root.put("runs", runs);
        root.put("warmupRuns", 1);
        root.put("defaultLiveMode", "DIRECT_SINGLE_WRITER");
        root.put("coordinatedOmissionCorrection", "expected interval correction");
        root.set("median", mapper.valueToTree(median));
        ArrayNode allRuns = root.putArray("allRuns");
        results.forEach(result -> allRuns.add(mapper.valueToTree(result)));
        Files.writeString(
                json,
                mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root),
                StandardCharsets.UTF_8
        );
        Files.writeString(markdown, markdown(input, median), StandardCharsets.UTF_8);
        System.out.println("FULL_PIPELINE_RESULT json=" + json + " markdown=" + markdown);
    }

    private static String markdown(Path input, FullPipelineBenchmarkResult result) {
        StringBuilder output = new StringBuilder();
        output.append("# Full Pipeline Benchmark\n\n");
        output.append("- Input: `").append(input).append("`\n");
        output.append("- Default live mode: `DIRECT_SINGLE_WRITER`\n");
        output.append("- Records: ").append(result.records()).append("\n");
        output.append(String.format(Locale.ROOT,
                "- Throughput: %.0f events/s%n", result.throughputPerSecond()));
        output.append(String.format(Locale.ROOT,
                "- Allocation: %.1f bytes/message%n", result.allocationBytesPerMessage()));
        output.append("- GC: ").append(result.gcCount()).append(" collections, ")
                .append(result.gcPauseMillis()).append(" ms\n");
        output.append("- Replay parity: ").append(result.replayParity()).append("\n\n");
        output.append("| Stage | p50 us | p95 us | p99 us | p99.9 us | max us |\n");
        output.append("|---|---:|---:|---:|---:|---:|\n");
        result.stageLatency().forEach((stage, latency) -> output.append(String.format(
                Locale.ROOT,
                "| %s | %.2f | %.2f | %.2f | %.2f | %.2f |%n",
                stage,
                latency.p50Micros(),
                latency.p95Micros(),
                latency.p99Micros(),
                latency.p999Micros(),
                latency.maxMicros()
        )));
        output.append("\nBootstrap p99: ")
                .append(String.format(Locale.ROOT, "%.2f us", result.bootstrapLatency().p99Micros()));
        output.append("\nIncremental p99: ")
                .append(String.format(Locale.ROOT, "%.2f us", result.incrementalLatency().p99Micros()));
        output.append("\n");
        return output.toString();
    }

    private static int positiveInt(String value) {
        int parsed = Integer.parseInt(value);
        if (parsed <= 0) {
            throw new IllegalArgumentException("runs must be positive");
        }
        return parsed;
    }
}

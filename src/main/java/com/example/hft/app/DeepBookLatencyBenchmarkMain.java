package com.example.hft.app;

import com.example.hft.datasource.deepbook.DeepBookSourceCatalog;
import com.example.hft.datasource.deepbook.DeepBookSourceDefinition;
import com.example.hft.datasource.deepbook.runtime.DeepBookReplayBenchmark;
import com.example.hft.datasource.deepbook.runtime.RawEnvelope;
import com.example.hft.datasource.deepbook.runtime.RawJournalRecordReader;
import com.example.hft.datasource.deepbook.runtime.ReplayBenchmarkResult;
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


public final class DeepBookLatencyBenchmarkMain {
    private static final long DEFAULT_TARGET_RECORDS = 500_000L;
    private static final int DEFAULT_RUNS = 5;
    private static final int SNAPSHOT_LEVELS = 10;

    private DeepBookLatencyBenchmarkMain() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            throw new IllegalArgumentException(
                    "usage: DeepBookLatencyBenchmarkMain <raw.jsonl> [workers] [targetRecords] [runs] [output.json]");
        }
        Path rawPath = Path.of(args[0]);
        int workers = args.length > 1 ? positiveInt(args[1], "workers")
                : Math.min(4, Runtime.getRuntime().availableProcessors());
        long targetRecords = args.length > 2 ? positiveLong(args[2], "targetRecords")
                : DEFAULT_TARGET_RECORDS;
        int runs = args.length > 3 ? positiveInt(args[3], "runs") : DEFAULT_RUNS;
        Path output = args.length > 4 ? Path.of(args[4]) : Path.of(
                "data/deep-book-latency-v24-"
                        + Instant.now().toString().replace(":", "").replace(".", "")
                        + ".json");

        ObjectMapper mapper = new ObjectMapper();
        List<RawEnvelope> records = RawJournalRecordReader.readAll(rawPath, mapper);

        int cycles = Math.toIntExact(Math.max(1L,
                (targetRecords + records.size() - 1L) / records.size()));
        long measuredRecords = Math.multiplyExact((long) records.size(), cycles);
        List<DeepBookSourceDefinition> sources = DeepBookSourceCatalog.defaultSources();
        DeepBookReplayBenchmark benchmark = new DeepBookReplayBenchmark(
                sources, records, SNAPSHOT_LEVELS, mapper);

        int warmupCycles = Math.max(1, Math.min(cycles, 20));
        benchmark.runDirect(warmupCycles);
        DeepBookReplayBenchmark.BenchmarkRun warmPartitioned =
                benchmark.runPartitioned(warmupCycles, workers);
        DeepBookReplayBenchmark.BenchmarkRun warmDirect = benchmark.runDirect(warmupCycles);
        DeepBookReplayBenchmark.requireSameBooks(warmDirect.replay(), warmPartitioned.replay());

        List<ReplayBenchmarkResult> directResults = new ArrayList<>();
        List<ReplayBenchmarkResult> partitionedResults = new ArrayList<>();
        for (int run = 1; run <= runs; run++) {
            DeepBookReplayBenchmark.BenchmarkRun direct = benchmark.runDirect(cycles);
            DeepBookReplayBenchmark.BenchmarkRun partitioned =
                    benchmark.runPartitioned(cycles, workers);
            DeepBookReplayBenchmark.requireSameBooks(direct.replay(), partitioned.replay());
            directResults.add(direct.metrics());
            partitionedResults.add(partitioned.metrics());
            print("run=" + run, direct.metrics());
            print("run=" + run, partitioned.metrics());
        }

        ReplayBenchmarkResult directMedian = medianByThroughput(directResults);
        ReplayBenchmarkResult partitionedMedian = medianByThroughput(partitionedResults);
        double throughputSpeedup = partitionedMedian.throughputPerSecond()
                / directMedian.throughputPerSecond();
        double p99E2eRatio = partitionedMedian.endToEndLatency().p99Micros()
                / directMedian.endToEndLatency().p99Micros();

        ObjectNode root = mapper.createObjectNode();
        root.put("rawFile", rawPath.toString());
        root.put("baseRecords", records.size());
        root.put("cycles", cycles);
        root.put("measuredRecordsPerRun", measuredRecords);
        root.put("workers", workers);
        root.put("runs", runs);
        root.put("bookParity", true);
        root.put("throughputSpeedup", throughputSpeedup);
        root.put("p99EndToEndRatio", p99E2eRatio);
        root.set("directMedian", mapper.valueToTree(directMedian));
        root.set("partitionedMedian", mapper.valueToTree(partitionedMedian));
        ArrayNode allRuns = root.putArray("allRuns");
        directResults.forEach(item -> allRuns.add(mapper.valueToTree(item)));
        partitionedResults.forEach(item -> allRuns.add(mapper.valueToTree(item)));
        Files.createDirectories(output.toAbsolutePath().getParent());
        Files.writeString(output,
                mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root),
                StandardCharsets.UTF_8);

        System.out.printf(Locale.ROOT,
                "BENCHMARK_RESULT recordsPerRun=%d workers=%d parity=true throughputSpeedup=%.3fx p99E2eRatio=%.3fx output=%s%n",
                measuredRecords, workers, throughputSpeedup, p99E2eRatio, output);
    }

    private static ReplayBenchmarkResult medianByThroughput(List<ReplayBenchmarkResult> values) {
        return values.stream()
                .sorted(Comparator.comparingDouble(ReplayBenchmarkResult::throughputPerSecond))
                .toList()
                .get(values.size() / 2);
    }

    private static void print(String prefix, ReplayBenchmarkResult result) {
        System.out.printf(Locale.ROOT,
                "%s mode=%s workers=%d records=%d elapsedMs=%.2f throughput=%.0f/s queueP99Us=%.2f processP99Us=%.2f e2eP99Us=%.2f backpressure=%d blockedMs=%.2f%n",
                prefix,
                result.mode(),
                result.workers(),
                result.records(),
                result.elapsedMillis(),
                result.throughputPerSecond(),
                result.queueLatency().p99Micros(),
                result.processingLatency().p99Micros(),
                result.endToEndLatency().p99Micros(),
                result.backpressureEvents(),
                result.producerBlockedMillis());
    }

    private static int positiveInt(String value, String label) {
        long parsed = positiveLong(value, label);
        return Math.toIntExact(parsed);
    }

    private static long positiveLong(String value, String label) {
        long parsed = Long.parseLong(value);
        if (parsed <= 0L) {
            throw new IllegalArgumentException(label + " must be positive");
        }
        return parsed;
    }
}
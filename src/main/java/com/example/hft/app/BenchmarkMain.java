package com.example.hft.app;

import com.example.hft.benchmark.BenchmarkResult;
import com.example.hft.marketdata.model.Quote;
import com.example.hft.marketdata.source.QuoteGenerator;
import com.example.hft.pipeline.JctoolsSpmcQueuePipeline;
import com.example.hft.pipeline.JctoolsSpscPartitionedPipeline;
import com.example.hft.pipeline.PartitionedQueuePipeline;
import com.example.hft.pipeline.QuotePipeline;
import com.example.hft.pipeline.SequentialPipeline;
import com.example.hft.pipeline.SharedQueuePipeline;
import java.util.List;



public final class BenchmarkMain {
    private static final int DEFAULT_QUOTE_COUNT = 200_000;
    private static final int DEFAULT_WORKER_COUNT = 4;
    private static final int WARMUP_RUNS = 1;
    private static final int MEASURED_RUNS = 3;

    private BenchmarkMain() {
    }

    public static void main(String[] args) throws InterruptedException {
        int quoteCount = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_QUOTE_COUNT;
        int workerCount = args.length > 1 ? Integer.parseInt(args[1]) : DEFAULT_WORKER_COUNT;
        List<Quote> quotes = QuoteGenerator.generate(quoteCount);
        List<QuotePipeline> pipelines = List.of(
                new SequentialPipeline(),
                new SharedQueuePipeline(),
                new PartitionedQueuePipeline(),
                new JctoolsSpmcQueuePipeline(),
                new JctoolsSpscPartitionedPipeline()
        );

        System.out.println("benchmark quotes=" + quoteCount + " workers=" + workerCount + " warmups=" + WARMUP_RUNS + " runs=" + MEASURED_RUNS);
        for (int i = 0; i < WARMUP_RUNS; i++) {
            for (QuotePipeline pipeline : pipelines) {
                pipeline.run(quotes, workerCount);
            }
        }

        BenchmarkResult[] lastResults = new BenchmarkResult[pipelines.size()];
        for (int run = 1; run <= MEASURED_RUNS; run++) {
            System.out.println("run " + run);
            for (int i = 0; i < pipelines.size(); i++) {
                BenchmarkResult result = pipelines.get(i).run(quotes, workerCount);
                lastResults[i] = result;
                System.out.println(result.toDisplayLine());
            }
        }

        System.out.println("module deltas from last measured run");
        BenchmarkResult lastBaseline = lastResults[0];
        for (BenchmarkResult result : lastResults) {
            System.out.println(result.deltaLine(lastBaseline));
        }
    }
}

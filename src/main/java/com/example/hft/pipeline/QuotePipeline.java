package com.example.hft.pipeline;

import com.example.hft.benchmark.BenchmarkResult;
import com.example.hft.marketdata.model.Quote;
import java.util.List;



public interface QuotePipeline {
    String name();

    BenchmarkResult run(List<Quote> quotes, int workerCount) throws InterruptedException;
}

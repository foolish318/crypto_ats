package com.example.hft.pipeline;

import com.example.hft.benchmark.BenchmarkResult;
import com.example.hft.benchmark.WorkerMetrics;
import com.example.hft.marketdata.model.Quote;
import com.example.hft.marketdata.model.TradingSignal;
import com.example.hft.strategy.MarketDataProcessor;
import com.example.hft.strategy.QuoteValidator;
import com.example.hft.strategy.TradingDecisionEngine;
import java.util.List;



public final class SequentialPipeline implements QuotePipeline {
    @Override
    public String name() {
        return "v1-sequential";
    }

    @Override
    public BenchmarkResult run(List<Quote> quotes, int workerCount) {
        WorkerMetrics metrics = new WorkerMetrics(quotes.size());
        MarketDataProcessor processor = new MarketDataProcessor(new QuoteValidator(), new TradingDecisionEngine());

        long runStart = System.nanoTime();
        for (Quote quote : quotes) {
            long start = System.nanoTime();
            TradingSignal signal = processor.signalFor(quote, metrics.moduleTiming());
            long end = System.nanoTime();
            metrics.record(signal, 0, end - start, end - start);
        }
        long runEnd = System.nanoTime();

        return BenchmarkResult.of(name(), 1, quotes.size(), runEnd - runStart, 0, new WorkerMetrics[] {metrics});
    }
}

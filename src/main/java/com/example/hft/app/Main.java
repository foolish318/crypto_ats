package com.example.hft.app;

import com.example.hft.marketdata.model.Quote;
import com.example.hft.marketdata.source.MarketDataFeed;
import com.example.hft.pipeline.ConcurrentQuoteRunner;
import com.example.hft.pipeline.ProcessingStats;
import com.example.hft.strategy.MarketDataProcessor;
import com.example.hft.strategy.QuoteValidator;
import com.example.hft.strategy.TradingDecisionEngine;
import java.util.List;



public final class Main {
    private Main() {
    }

    public static void main(String[] args) throws InterruptedException {
        List<Quote> quotes = List.of(
                Quote.of(1, "BARC.L", 21_240, 1_000, 21_246, 800, 0),
                Quote.of(2, "BARC.L", 21_239, 2_500, 21_244, 400, 0),
                Quote.of(3, "BARC.L", 21_238, 500, 21_255, 3_000, 0),
                Quote.of(4, "VOD.L", 7_130, 8_000, 7_132, 4_500, 0),
                Quote.of(5, "HSBA.L", 69_840, 600, 69_850, 1_900, 0)
        );

        MarketDataProcessor processor = new MarketDataProcessor(new QuoteValidator(), new TradingDecisionEngine());
        for (Quote quote : quotes) {
            System.out.println(processor.analyze(quote).toDisplayLine());
        }

        ConcurrentQuoteRunner runner = new ConcurrentQuoteRunner(2, new MarketDataFeed(quotes), processor);
        ProcessingStats stats = runner.run();
        System.out.println(stats.toDisplayLine());
    }
}

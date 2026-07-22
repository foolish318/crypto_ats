package com.example.hft.app;

import com.example.hft.exchange.binance.BinanceBookTickerSource;
import com.example.hft.marketdata.model.Quote;
import com.example.hft.marketdata.source.QuoteSource;
import com.example.hft.strategy.MarketDataProcessor;
import com.example.hft.strategy.QuoteValidator;
import com.example.hft.strategy.TradingDecisionEngine;
import java.util.List;



public final class BinanceReplayMain {
    private static final List<String> DEFAULT_SYMBOLS = List.of("BTCUSDT", "ETHUSDT", "BNBUSDT");
    private static final int DEFAULT_LIMIT = 10;

    private BinanceReplayMain() {
    }

    public static void main(String[] args) throws Exception {
        int limit = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_LIMIT;
        List<String> symbols = args.length > 1 ? List.of(args[1].split(",")) : DEFAULT_SYMBOLS;

        QuoteSource source = new BinanceBookTickerSource(symbols);
        List<Quote> quotes = source.load(limit);
        MarketDataProcessor processor = new MarketDataProcessor(new QuoteValidator(), new TradingDecisionEngine());

        System.out.println("loaded " + quotes.size() + " Binance bookTicker quotes from " + symbols);
        for (Quote quote : quotes) {
            System.out.println(processor.analyze(quote).toDisplayLine());
        }
    }
}

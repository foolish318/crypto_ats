package com.example.hft.marketdata.source;

import com.example.hft.marketdata.model.Quote;
import java.util.ArrayList;
import java.util.List;



public final class QuoteGenerator {
    private static final String[] SYMBOLS = {
            "BARC.L", "VOD.L", "HSBA.L", "LLOY.L", "BP.L", "SHEL.L", "AZN.L", "RIO.L"
    };

    private QuoteGenerator() {
    }

    public static List<Quote> generate(int count) {
        List<Quote> quotes = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String symbol = SYMBOLS[i % SYMBOLS.length];
            long baseTicks = 10_000 + ((long) (i % 5_000) * 3);
            long spreadTicks = 1 + (i % 20);
            int bidSize = 100 + ((i * 37) % 10_000);
            int askSize = 100 + ((i * 53) % 10_000);
            quotes.add(Quote.of(i, symbol, baseTicks, bidSize, baseTicks + spreadTicks, askSize, i));
        }
        return quotes;
    }
}

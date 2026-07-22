package com.example.hft.marketdata.source;

import com.example.hft.marketdata.model.Quote;
import java.util.List;



public interface QuoteSource {
    List<Quote> load(int limit) throws Exception;
}

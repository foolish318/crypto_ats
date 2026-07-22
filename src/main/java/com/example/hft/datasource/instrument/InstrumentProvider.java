package com.example.hft.datasource.instrument;

import java.util.List;
import java.util.Optional;


public interface InstrumentProvider {
    String exchange();

    List<Instrument> instruments();

    default Optional<Instrument> findByExchangeSymbol(String exchangeSymbol) {
        return instruments().stream()
                .filter(instrument -> instrument.exchangeSymbol().equals(exchangeSymbol))
                .findFirst();
    }

    default Optional<Instrument> findByCanonicalSymbol(String canonicalSymbol) {
        return instruments().stream()
                .filter(instrument -> instrument.canonicalSymbol().equals(canonicalSymbol))
                .findFirst();
    }
}

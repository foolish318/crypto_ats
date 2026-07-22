package com.example.hft.datasource.instrument;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;


public final class SymbolMapper {
    private final Map<String, Instrument> byVenueKey = new HashMap<>();
    private final Map<String, Instrument> byCanonicalKey = new HashMap<>();

    public SymbolMapper(List<Instrument> instruments) {
        for (Instrument instrument : instruments) {
            byVenueKey.put(venueKey(instrument.exchange(), instrument.exchangeSymbol()), instrument);
            byCanonicalKey.put(venueKey(instrument.exchange(), instrument.canonicalSymbol()), instrument);
        }
    }

    public Optional<Instrument> byExchangeSymbol(String exchange, String exchangeSymbol) {
        return Optional.ofNullable(byVenueKey.get(venueKey(exchange, exchangeSymbol)));
    }

    public Optional<Instrument> byCanonicalSymbol(String exchange, String canonicalSymbol) {
        return Optional.ofNullable(byCanonicalKey.get(venueKey(exchange, canonicalSymbol)));
    }

    private static String venueKey(String exchange, String symbol) {
        return exchange + "|" + symbol;
    }
}

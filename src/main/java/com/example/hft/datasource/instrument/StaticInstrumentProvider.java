package com.example.hft.datasource.instrument;

import java.util.List;


public final class StaticInstrumentProvider implements InstrumentProvider {
    private final String exchange;
    private final List<Instrument> instruments;

    public StaticInstrumentProvider(String exchange, List<Instrument> instruments) {
        this.exchange = exchange;
        this.instruments = List.copyOf(instruments);
    }

    @Override
    public String exchange() {
        return exchange;
    }

    @Override
    public List<Instrument> instruments() {
        return instruments;
    }
}

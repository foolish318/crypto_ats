package com.example.hft.marketdata.model;


public enum StopMessage implements QuoteEvent {
    INSTANCE;

    @Override
    public boolean isStop() {
        return true;
    }
}

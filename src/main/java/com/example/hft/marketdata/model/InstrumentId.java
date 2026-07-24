package com.example.hft.marketdata.model;

public record InstrumentId(String value) implements Comparable<InstrumentId> {
    public InstrumentId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("instrument id is required");
        }
    }

    @Override
    public int compareTo(InstrumentId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
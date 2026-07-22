package com.example.hft.marketdata.model;

import java.util.Objects;



public final class Price implements Comparable<Price> {
    private static final int TICKS_PER_UNIT = 100;

    private final long ticks;

    private Price(long ticks) {
        this.ticks = ticks;
    }

    public static Price fromTicks(long ticks) {
        if (ticks <= 0) {
            throw new IllegalArgumentException("price ticks must be positive");
        }
        return new Price(ticks);
    }

    static Price fromRawTicks(long ticks) {
        return new Price(ticks);
    }

    public long ticks() {
        return ticks;
    }

    public Price minus(Price other) {
        return new Price(ticks - other.ticks);
    }

    public static Price average(Price first, Price second) {
        return new Price((first.ticks + second.ticks) / 2);
    }

    @Override
    public int compareTo(Price other) {
        return Long.compare(ticks, other.ticks);
    }

    @Override
    public String toString() {
        long absoluteTicks = Math.abs(ticks);
        long whole = absoluteTicks / TICKS_PER_UNIT;
        long fraction = absoluteTicks % TICKS_PER_UNIT;
        String sign = ticks < 0 ? "-" : "";
        return sign + whole + "." + (fraction < 10 ? "0" : "") + fraction;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Price price)) {
            return false;
        }
        return ticks == price.ticks;
    }

    @Override
    public int hashCode() {
        return Objects.hash(ticks);
    }
}

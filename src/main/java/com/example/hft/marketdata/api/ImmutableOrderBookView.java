package com.example.hft.marketdata.api;

import com.example.hft.marketdata.model.BookHealth;
import com.example.hft.marketdata.model.BookLevel;
import com.example.hft.marketdata.model.BookSnapshot;
import com.example.hft.marketdata.model.InstrumentId;
import com.example.hft.marketdata.model.Side;
import com.example.hft.marketdata.model.Venue;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;
import java.util.Optional;
import java.util.function.LongSupplier;

public final class ImmutableOrderBookView implements OrderBookView {
    private final BookSnapshot snapshot;
    private final LongSupplier clock;

    public ImmutableOrderBookView(BookSnapshot snapshot) {
        this(snapshot, System::currentTimeMillis);
    }

    public ImmutableOrderBookView(BookSnapshot snapshot, LongSupplier clock) {
        if (snapshot == null || clock == null) {
            throw new IllegalArgumentException("snapshot and clock are required");
        }
        this.snapshot = snapshot;
        this.clock = clock;
    }

    public BookSnapshot snapshot() {
        return snapshot;
    }

    public ImmutableOrderBookView withHealth(BookHealth health) {
        return new ImmutableOrderBookView(new BookSnapshot(
                snapshot.header(), snapshot.bookVersion(), health,
                snapshot.lastUpdateEpochMillis(), snapshot.lastAppliedMonotonicNanos(),
                snapshot.bids(), snapshot.asks()), clock);
    }

    @Override
    public Venue venue() {
        return snapshot.header().venue();
    }

    @Override
    public InstrumentId instrument() {
        return snapshot.header().instrumentId();
    }

    @Override
    public String venueSymbol() {
        return snapshot.header().venueSymbol();
    }

    @Override
    public long bookVersion() {
        return snapshot.bookVersion();
    }

    @Override
    public Long sourceSequence() {
        return snapshot.header().sourceSequence();
    }

    @Override
    public long localSequence() {
        return snapshot.header().localSequence();
    }

    @Override
    public long streamEpoch() {
        return snapshot.header().streamEpoch();
    }

    @Override
    public BookHealth health() {
        return snapshot.health();
    }

    @Override
    public long exchangeTimestamp() {
        return snapshot.header().exchangeEpochMillis();
    }

    @Override
    public long localReceiveTimestamp() {
        return snapshot.header().receiveEpochMillis();
    }

    @Override
    public long lastUpdateTime() {
        return snapshot.lastUpdateEpochMillis();
    }

    @Override
    public long ageMillis() {
        return Math.max(0L, clock.getAsLong() - localReceiveTimestamp());
    }

    @Override
    public Optional<BookLevel> bestBid() {
        return snapshot.bids().isEmpty() ? Optional.empty() : Optional.of(snapshot.bids().get(0));
    }

    @Override
    public Optional<BookLevel> bestAsk() {
        return snapshot.asks().isEmpty() ? Optional.empty() : Optional.of(snapshot.asks().get(0));
    }

    @Override
    public Optional<BigDecimal> spread() {
        return bestBid().flatMap(bid -> bestAsk().map(ask -> ask.price().subtract(bid.price())));
    }

    @Override
    public Optional<BigDecimal> mid() {
        return bestBid().flatMap(bid -> bestAsk().map(ask ->
                ask.price().add(bid.price()).divide(BigDecimal.valueOf(2L), MathContext.DECIMAL64)));
    }

    @Override
    public List<BookLevel> topBids(int depth) {
        return top(snapshot.bids(), depth);
    }

    @Override
    public List<BookLevel> topAsks(int depth) {
        return top(snapshot.asks(), depth);
    }

    @Override
    public BigDecimal depthAt(Side side, BigDecimal price) {
        if (side == null || price == null) {
            throw new IllegalArgumentException("side and price are required");
        }
        List<BookLevel> levels = side == Side.BID ? snapshot.bids() : snapshot.asks();
        return levels.stream()
                .filter(level -> level.price().compareTo(price) == 0)
                .map(BookLevel::quantity)
                .findFirst()
                .orElse(BigDecimal.ZERO);
    }

    private static List<BookLevel> top(List<BookLevel> levels, int depth) {
        if (depth < 0) {
            throw new IllegalArgumentException("depth must be non-negative");
        }
        return List.copyOf(levels.subList(0, Math.min(depth, levels.size())));
    }
}
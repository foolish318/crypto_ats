package com.example.hft.marketdata.api;

import com.example.hft.marketdata.model.BookHealth;
import com.example.hft.marketdata.model.BookLevel;
import com.example.hft.marketdata.model.InstrumentId;
import com.example.hft.marketdata.model.Side;
import com.example.hft.marketdata.model.Venue;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface OrderBookView {
    Venue venue();

    InstrumentId instrument();

    String venueSymbol();

    long bookVersion();

    Long sourceSequence();

    long localSequence();

    long streamEpoch();

    BookHealth health();

    long exchangeTimestamp();

    long localReceiveTimestamp();

    long lastUpdateTime();

    long ageMillis();

    Optional<BookLevel> bestBid();

    Optional<BookLevel> bestAsk();

    Optional<BigDecimal> spread();

    Optional<BigDecimal> mid();

    List<BookLevel> topBids(int depth);

    List<BookLevel> topAsks(int depth);

    BigDecimal depthAt(Side side, BigDecimal price);
}
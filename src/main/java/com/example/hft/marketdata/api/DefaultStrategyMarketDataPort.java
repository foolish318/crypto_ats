package com.example.hft.marketdata.api;

import com.example.hft.datasource.deepbook.runtime.AcceptedLocalBookEvent;
import com.example.hft.datasource.deepbook.runtime.BookAvailabilityEvent;
import com.example.hft.datasource.engine.MarketDataListener;
import com.example.hft.datasource.normalizer.NormalizedMarketDataEvent;
import com.example.hft.marketdata.model.BookHealth;
import com.example.hft.marketdata.model.BookSnapshot;
import com.example.hft.marketdata.model.BookStatusChange;
import com.example.hft.marketdata.model.InstrumentId;
import com.example.hft.marketdata.model.PublicTrade;
import com.example.hft.marketdata.model.Venue;
import com.example.hft.marketdata.recording.NormalizedEventSink;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

public final class DefaultStrategyMarketDataPort
        implements StrategyMarketDataPort, MarketDataListener {
    private final Map<BookKey, ImmutableOrderBookView> books = new ConcurrentHashMap<>();
    private final Map<BookKey, PublicTrade> trades = new ConcurrentHashMap<>();
    private final Map<BookKey, Long> latestEpoch = new ConcurrentHashMap<>();
    private final Map<BookKey, BookHealth> health = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<StrategyMarketDataListener> listeners =
            new CopyOnWriteArrayList<>();
    private final LongSupplier clock;
    private final NormalizedEventSink recorder;
    private final AtomicLong bookUpdates = new AtomicLong();
    private final AtomicLong tradeUpdates = new AtomicLong();
    private final AtomicLong statusUpdates = new AtomicLong();
    private final AtomicLong listenerErrors = new AtomicLong();

    public DefaultStrategyMarketDataPort() {
        this(System::currentTimeMillis, NormalizedEventSink.noop());
    }

    public DefaultStrategyMarketDataPort(LongSupplier clock) {
        this(clock, NormalizedEventSink.noop());
    }

    public DefaultStrategyMarketDataPort(LongSupplier clock, NormalizedEventSink recorder) {
        if (clock == null || recorder == null) {
            throw new IllegalArgumentException("clock and recorder are required");
        }
        this.clock = clock;
        this.recorder = recorder;
    }

    @Override
    public void onMarketData(NormalizedMarketDataEvent event) {
        if (event instanceof AcceptedLocalBookEvent accepted) {
            applyBook(CanonicalMarketDataMapper.book(accepted), true);
        } else if (event instanceof PublicTrade trade) {
            applyTrade(trade, true);
        }
    }

    @Override
    public void onBookAvailability(BookAvailabilityEvent event) {
        BookKey key = new BookKey(
                Venue.fromExchange(event.exchange()),
                new InstrumentId(event.canonicalInstrumentId())
        );
        long currentEpoch = latestEpoch.getOrDefault(key, -1L);
        if (event.generation() < currentEpoch) {
            return;
        }
        latestEpoch.put(key, event.generation());
        BookHealth previous = health.get(key);
        BookStatusChange change = CanonicalMarketDataMapper.status(event, previous);
        health.put(key, change.health());
        books.computeIfPresent(key, (ignored, current) ->
                current.streamEpoch() <= event.generation()
                        ? current.withHealth(change.health())
                        : current);
        recorder.recordStatus(change);
        statusUpdates.incrementAndGet();
        for (StrategyMarketDataListener listener : listeners) {
            invoke(() -> listener.onBookStatusChanged(change));
        }
    }

    public void applyRecordedBook(BookSnapshot book) {
        applyBook(book, false);
    }

    public void applyRecordedTrade(PublicTrade trade) {
        applyTrade(trade, false);
    }

    public void applyRecordedStatus(BookStatusChange status) {
        BookKey key = new BookKey(status.venue(), status.instrumentId());
        latestEpoch.merge(key, status.streamEpoch(), Math::max);
        health.put(key, status.health());
        books.computeIfPresent(key, (ignored, current) -> current.withHealth(status.health()));
        statusUpdates.incrementAndGet();
        for (StrategyMarketDataListener listener : listeners) {
            invoke(() -> listener.onBookStatusChanged(status));
        }
    }

    private void applyBook(BookSnapshot book, boolean record) {
        BookKey key = new BookKey(book.header().venue(), book.header().instrumentId());
        long currentEpoch = latestEpoch.getOrDefault(key, -1L);
        if (book.header().streamEpoch() < currentEpoch) {
            return;
        }
        latestEpoch.put(key, book.header().streamEpoch());
        health.put(key, BookHealth.LIVE);
        ImmutableOrderBookView view = new ImmutableOrderBookView(book, clock);
        books.put(key, view);
        if (record) {
            recorder.recordBook(book);
        }
        bookUpdates.incrementAndGet();
        BookUpdateNotification notification = new BookUpdateNotification(
                view.venue(), view.instrument(), view.bookVersion(), view.localSequence(),
                view.exchangeTimestamp(), view.localReceiveTimestamp(),
                book.header().publishMonotonicNanos(), view.health()
        );
        for (StrategyMarketDataListener listener : listeners) {
            invoke(() -> listener.onBookUpdated(notification));
        }
    }

    private void applyTrade(PublicTrade trade, boolean record) {
        BookKey key = new BookKey(trade.header().venue(), trade.header().instrumentId());
        PublicTrade current = trades.get(key);
        if (current != null
                && trade.header().streamEpoch() < current.header().streamEpoch()) {
            return;
        }
        trades.put(key, trade);
        if (record) {
            recorder.recordTrade(trade);
        }
        tradeUpdates.incrementAndGet();
        for (StrategyMarketDataListener listener : listeners) {
            invoke(() -> listener.onTrade(trade));
        }
    }

    @Override
    public Optional<OrderBookView> getBook(Venue venue, InstrumentId instrument) {
        return Optional.ofNullable(books.get(new BookKey(venue, instrument)))
                .map(view -> view);
    }

    @Override
    public MultiVenueBookView getBooks(InstrumentId instrument) {
        Map<Venue, OrderBookView> result = new EnumMap<>(Venue.class);
        books.forEach((key, value) -> {
            if (key.instrument.equals(instrument)) {
                result.put(key.venue, value);
            }
        });
        return new MultiVenueBookView(instrument, clock.getAsLong(), result);
    }

    @Override
    public Optional<PublicTrade> latestTrade(Venue venue, InstrumentId instrument) {
        return Optional.ofNullable(trades.get(new BookKey(venue, instrument)));
    }

    @Override
    public void subscribe(StrategyMarketDataListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("listener is required");
        }
        listeners.addIfAbsent(listener);
    }

    @Override
    public void unsubscribe(StrategyMarketDataListener listener) {
        listeners.remove(listener);
    }

    public long bookUpdates() {
        return bookUpdates.get();
    }

    public long tradeUpdates() {
        return tradeUpdates.get();
    }

    public long statusUpdates() {
        return statusUpdates.get();
    }

    public long listenerErrors() {
        return listenerErrors.get();
    }

    private void invoke(Runnable callback) {
        try {
            callback.run();
        } catch (Throwable error) {
            listenerErrors.incrementAndGet();
        }
    }

    private record BookKey(Venue venue, InstrumentId instrument) {
    }
}
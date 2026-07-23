package com.example.hft.datasource.deepbook.runtime;

import com.example.hft.datasource.engine.MarketDataListener;
import com.example.hft.datasource.normalizer.NormalizedMarketDataEvent;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;


public final class CrossExchangeBookView implements MarketDataListener {
    private static final Duration DEFAULT_FRESHNESS = Duration.ofSeconds(10);
    private static final Duration DEFAULT_COHERENCE = Duration.ofMillis(250);

    private final Map<String, VenueState> latestBySource = new ConcurrentHashMap<>();
    private final long freshnessThresholdMillis;
    private final long coherenceThresholdMillis;
    private final LongSupplier clock;

    public CrossExchangeBookView() {
        this(DEFAULT_FRESHNESS, DEFAULT_COHERENCE, System::currentTimeMillis);
    }

    public CrossExchangeBookView(Duration freshnessThreshold, Duration coherenceThreshold) {
        this(freshnessThreshold, coherenceThreshold, System::currentTimeMillis);
    }

    CrossExchangeBookView(
            Duration freshnessThreshold,
            Duration coherenceThreshold,
            LongSupplier clock
    ) {
        if (freshnessThreshold.isNegative() || freshnessThreshold.isZero()
                || coherenceThreshold.isNegative()) {
            throw new IllegalArgumentException("view thresholds are invalid");
        }
        this.freshnessThresholdMillis = freshnessThreshold.toMillis();
        this.coherenceThresholdMillis = coherenceThreshold.toMillis();
        this.clock = clock;
    }

    @Override
    public void onMarketData(NormalizedMarketDataEvent event) {
        if (!(event instanceof AcceptedLocalBookEvent accepted)) {
            return;
        }
        latestBySource.compute(accepted.source(), (ignored, current) -> {
            if (current != null && accepted.generation() < current.generation) {
                return current;
            }
            if (current != null
                    && accepted.generation() == current.generation
                    && current.state != BookAvailabilityState.LIVE
                    && !current.restoreAllowed) {
                return current;
            }
            return VenueState.live(accepted);
        });
    }

    @Override
    public void onBookAvailability(BookAvailabilityEvent event) {
        latestBySource.compute(event.sourceId(), (ignored, current) -> {
            if (current != null && event.generation() < current.generation) {
                return current;
            }
            if (event.live()) {
                return current;
            }
            return current == null ? VenueState.availability(event) : current.withState(event);
        });
    }

    public Optional<ConsolidatedBookSnapshot> consolidated(String canonicalInstrumentId) {
        return consolidated(canonicalInstrumentId, clock.getAsLong());
    }

    public Optional<ConsolidatedBookSnapshot> consolidated(
            String canonicalInstrumentId,
            long observedEpochMillis
    ) {
        Set<String> known = new HashSet<>();
        List<VenueBookSnapshot> active = new ArrayList<>();
        for (VenueState state : latestBySource.values()) {
            if (!canonicalInstrumentId.equals(state.canonicalInstrumentId)) {
                continue;
            }
            known.add(state.sourceId);
            VenueBookSnapshot snapshot = state.snapshot(
                    observedEpochMillis,
                    freshnessThresholdMillis
            );
            if (snapshot != null) {
                active.add(snapshot);
            }
        }
        if (known.isEmpty()) {
            return Optional.empty();
        }
        active.sort(Comparator.comparing(VenueBookSnapshot::exchange)
                .thenComparing(VenueBookSnapshot::venueSymbol));

        VenueBookSnapshot bidVenue = active.stream()
                .filter(item -> item.bestBid() != null)
                .max(Comparator.comparing((VenueBookSnapshot item) -> item.bestBid().price())
                        .thenComparing(VenueBookSnapshot::exchange, Comparator.reverseOrder())
                        .thenComparing(VenueBookSnapshot::venueSymbol, Comparator.reverseOrder()))
                .orElse(null);
        VenueBookSnapshot askVenue = active.stream()
                .filter(item -> item.bestAsk() != null)
                .min(Comparator.comparing((VenueBookSnapshot item) -> item.bestAsk().price())
                        .thenComparing(VenueBookSnapshot::exchange)
                        .thenComparing(VenueBookSnapshot::venueSymbol))
                .orElse(null);

        DecimalBookLevel bestBid = bidVenue == null ? null : bidVenue.bestBid();
        DecimalBookLevel bestAsk = askVenue == null ? null : askVenue.bestAsk();
        BigDecimal spread = bestBid == null || bestAsk == null
                ? null
                : bestAsk.price().subtract(bestBid.price());
        boolean crossed = spread != null && spread.signum() < 0;
        boolean locked = spread != null && spread.signum() == 0;

        long minEventTime = Long.MAX_VALUE;
        long maxEventTime = Long.MIN_VALUE;
        for (VenueBookSnapshot venue : active) {
            long eventMillis = venue.eventTime().toEpochMilli();
            minEventTime = Math.min(minEventTime, eventMillis);
            maxEventTime = Math.max(maxEventTime, eventMillis);
        }
        long skew = active.size() < 2 ? 0L : maxEventTime - minEventTime;
        boolean coherent = active.size() < 2 || skew <= coherenceThresholdMillis;
        Instant watermark = active.isEmpty() ? null : Instant.ofEpochMilli(minEventTime);

        return Optional.of(new ConsolidatedBookSnapshot(
                canonicalInstrumentId,
                Instant.ofEpochMilli(observedEpochMillis),
                active,
                bestBid,
                bidVenue == null ? "" : venueId(bidVenue),
                bestAsk,
                askVenue == null ? "" : venueId(askVenue),
                spread,
                crossed,
                locked,
                active.size(),
                coherent,
                watermark,
                skew
        ));
    }

    public Collection<AcceptedLocalBookEvent> books() {
        long now = clock.getAsLong();
        return latestBySource.values().stream()
                .filter(state -> state.liveAndFresh(now, freshnessThresholdMillis))
                .map(state -> state.event)
                .sorted(Comparator.comparing(AcceptedLocalBookEvent::exchange)
                        .thenComparing(AcceptedLocalBookEvent::symbol))
                .toList();
    }

    public int size() {
        return books().size();
    }

    private static String venueId(VenueBookSnapshot venue) {
        return venue.exchange() + "|" + venue.venueSymbol();
    }

    private static final class VenueState {
        private final String sourceId;
        private final String exchange;
        private final String venueSymbol;
        private final String canonicalInstrumentId;
        private final long generation;
        private final BookAvailabilityState state;
        private final AcceptedLocalBookEvent event;
        private final boolean restoreAllowed;

        private VenueState(
                String sourceId,
                String exchange,
                String venueSymbol,
                String canonicalInstrumentId,
                long generation,
                BookAvailabilityState state,
                AcceptedLocalBookEvent event,
                boolean restoreAllowed
        ) {
            this.sourceId = sourceId;
            this.exchange = exchange;
            this.venueSymbol = venueSymbol;
            this.canonicalInstrumentId = canonicalInstrumentId;
            this.generation = generation;
            this.state = state;
            this.event = event;
            this.restoreAllowed = restoreAllowed;
        }

        private static VenueState live(AcceptedLocalBookEvent event) {
            return new VenueState(
                    event.source(), event.exchange(), event.symbol(),
                    event.canonicalInstrumentId(), event.generation(),
                    BookAvailabilityState.LIVE, event, false
            );
        }

        private static VenueState availability(BookAvailabilityEvent event) {
            return new VenueState(
                    event.sourceId(), event.exchange(), event.venueSymbol(),
                    event.canonicalInstrumentId(), event.generation(), event.state(), null,
                    event.state() == BookAvailabilityState.RECOVERING
            );
        }

        private VenueState withState(BookAvailabilityEvent availability) {
            AcceptedLocalBookEvent retained = event != null
                    && event.generation() == availability.generation()
                    ? event
                    : null;
            return new VenueState(
                    availability.sourceId(), availability.exchange(),
                    availability.venueSymbol(), availability.canonicalInstrumentId(),
                    availability.generation(), availability.state(), retained,
                    availability.state() == BookAvailabilityState.RECOVERING
                            && availability.generation() > generation
            );
        }

        private boolean liveAndFresh(long now, long freshnessMillis) {
            return state == BookAvailabilityState.LIVE
                    && event != null
                    && event.generation() == generation
                    && Math.max(0L, now - event.acceptedEpochMillis()) < freshnessMillis;
        }

        private VenueBookSnapshot snapshot(long now, long freshnessMillis) {
            if (!liveAndFresh(now, freshnessMillis)) {
                return null;
            }
            long age = Math.max(0L, now - event.acceptedEpochMillis());
            return new VenueBookSnapshot(
                    sourceId, exchange, venueSymbol, canonicalInstrumentId,
                    state, generation, event.sequence(),
                    event.book().exchangeTime(),
                    Instant.ofEpochMilli(event.acceptedEpochMillis()),
                    age, event.book().bestBid(), event.book().bestAsk(), event.book()
            );
        }
    }
}

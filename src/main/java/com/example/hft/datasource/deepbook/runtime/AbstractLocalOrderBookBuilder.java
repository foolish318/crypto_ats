package com.example.hft.datasource.deepbook.runtime;

import com.example.hft.datasource.book.BookQuality;
import com.example.hft.datasource.deepbook.DeepBookSourceDefinition;
import com.example.hft.datasource.instrument.Instrument;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.time.Duration;
import java.time.Instant;


abstract class AbstractLocalOrderBookBuilder implements LocalOrderBookBuilder {
    private static final Duration MAX_FUTURE_CLOCK_SKEW = Duration.ofSeconds(5);

    protected final DeepBookSourceDefinition source;
    protected final String canonicalInstrumentId;
    protected final MutableDecimalOrderBook book;
    protected final ObjectMapper mapper = new ObjectMapper()
            .setNodeFactory(JsonNodeFactory.withExactBigDecimals(true))
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    protected final Duration maxEventAge;

    protected BookQuality quality = BookQuality.EMPTY;
    protected long sequence = -1L;
    protected Instant exchangeTime = Instant.EPOCH;
    protected long acceptedMessages;
    protected long rejectedMessages;

    AbstractLocalOrderBookBuilder(DeepBookSourceDefinition source, Duration maxEventAge) {
        this(source, maxEventAge, null);
    }

    AbstractLocalOrderBookBuilder(
            DeepBookSourceDefinition source,
            Duration maxEventAge,
            Instrument instrument
    ) {
        if (instrument != null
                && (!source.exchange().equals(instrument.exchange())
                || !source.symbol().equals(instrument.exchangeSymbol()))) {
            throw new IllegalArgumentException("instrument metadata does not match source");
        }
        this.source = source;
        this.canonicalInstrumentId = instrument == null
                ? source.symbol()
                : instrument.canonicalSymbol();
        this.maxEventAge = maxEventAge;
        this.book = new MutableDecimalOrderBook(instrument);
    }

    @Override
    public final DeepBookSourceDefinition source() {
        return source;
    }

    @Override
    public final String canonicalInstrumentId() {
        return canonicalInstrumentId;
    }

    @Override
    public final BookQuality quality() {
        return quality;
    }

    @Override
    public final long acceptedMessages() {
        return acceptedMessages;
    }

    @Override
    public final long rejectedMessages() {
        return rejectedMessages;
    }

    @Override
    public final LocalBookSnapshot snapshot(int levels) {
        return new LocalBookSnapshot(
                source.id(),
                source.exchange(),
                source.symbol(),
                quality,
                sequence,
                exchangeTime,
                book.topBids(levels),
                book.topAsks(levels)
        );
    }

    protected final ParsedPayload parse(String payload) throws Exception {
        long start = System.nanoTime();
        JsonNode root = mapper.readTree(payload);
        return new ParsedPayload(root, System.nanoTime() - start);
    }

    protected final void requireFresh(Instant eventTime, long receivedEpochMillis) {
        Instant received = Instant.ofEpochMilli(receivedEpochMillis);
        Duration age = Duration.between(eventTime, received);
        if (age.compareTo(maxEventAge) > 0) {
            throw new IllegalArgumentException("event is stale by " + age.toMillis() + "ms");
        }
        if (age.compareTo(MAX_FUTURE_CLOCK_SKEW.negated()) < 0) {
            throw new IllegalArgumentException("event is too far in the future");
        }
    }

    protected final BookUpdateResult accepted(
            BookUpdateStatus status,
            long eventSequence,
            Instant eventTime,
            long parseNanos,
            long bookStartNanos,
            String detail
    ) {
        quality = BookQuality.LIVE;
        sequence = eventSequence;
        exchangeTime = eventTime;
        acceptedMessages++;
        return result(status, parseNanos, bookStartNanos, detail);
    }

    protected final BookUpdateResult rejected(
            BookUpdateStatus status,
            BookQuality rejectedQuality,
            long parseNanos,
            long bookStartNanos,
            String detail
    ) {
        quality = rejectedQuality;
        rejectedMessages++;
        return result(status, parseNanos, bookStartNanos, detail);
    }

    protected final BookUpdateResult parseFailure(long parseStartNanos, Exception error) {
        quality = BookQuality.DEGRADED;
        rejectedMessages++;
        return new BookUpdateResult(
                BookUpdateStatus.PARSE_FAILED,
                quality,
                sequence,
                exchangeTime.toEpochMilli(),
                System.nanoTime() - parseStartNanos,
                0L,
                error.getClass().getSimpleName() + ": " + error.getMessage()
        );
    }

    protected final BookUpdateResult ignored(long parseNanos, String detail) {
        return new BookUpdateResult(
                BookUpdateStatus.IGNORED,
                quality,
                sequence,
                exchangeTime.toEpochMilli(),
                parseNanos,
                0L,
                detail
        );
    }

    private BookUpdateResult result(
            BookUpdateStatus status,
            long parseNanos,
            long bookStartNanos,
            String detail
    ) {
        return new BookUpdateResult(
                status,
                quality,
                sequence,
                exchangeTime.toEpochMilli(),
                parseNanos,
                System.nanoTime() - bookStartNanos,
                detail
        );
    }

    protected record ParsedPayload(JsonNode root, long parseNanos) {
    }
}

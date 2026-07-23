package com.example.hft.datasource.deepbook.runtime;

import com.example.hft.datasource.book.BookQuality;


public final class BookPipeline {
    private final LocalOrderBookBuilder builder;
    private final LocalBookPublisher publisher;
    private final SessionHealth health;

    public BookPipeline(
            LocalOrderBookBuilder builder,
            LocalBookPublisher publisher,
            SessionHealth health
    ) {
        this.builder = builder;
        this.publisher = publisher;
        this.health = health;
    }

    public BookUpdateResult loadSnapshot(String payload, long receivedEpochMillis) {
        return builder.loadSnapshot(payload, receivedEpochMillis);
    }

    public BookUpdateResult onMessage(String payload, long receivedEpochMillis) {
        return builder.onMessage(payload, receivedEpochMillis);
    }

    public boolean publishIfEligible(
            BookUpdateResult result,
            long generation,
            long receivedNanos,
            long acceptedEpochMillis
    ) {
        return publisher.publishIfEligible(
                builder,
                result,
                health,
                generation,
                receivedNanos,
                acceptedEpochMillis
        );
    }


    public BookPublishResult publish(
            BookUpdateResult result,
            long generation,
            long receivedNanos,
            long acceptedEpochMillis
    ) {
        return publisher.publish(
                builder,
                result,
                health,
                generation,
                receivedNanos,
                acceptedEpochMillis
        );
    }
    public void availability(
            long generation,
            BookAvailabilityState state,
            String reason
    ) {
        publisher.availability(builder, generation, state, reason);
    }

    public LocalBookSnapshot snapshot(int levels) {
        return builder.snapshot(levels);
    }

    public BookQuality quality() {
        return builder.quality();
    }

    public void reset() {
        builder.reset();
    }
}

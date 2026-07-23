package com.example.hft.datasource.deepbook.runtime;

import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.LongSupplier;


public final class StaleWatchdog implements AutoCloseable {
    private final SessionHealth health;
    private final long staleThresholdMillis;
    private final Consumer<String> staleAction;
    private final LongSupplier clock;
    private final ScheduledFuture<?> task;

    public StaleWatchdog(
            ScheduledExecutorService scheduler,
            SessionHealth health,
            Duration staleThreshold,
            Consumer<String> staleAction
    ) {
        this(scheduler, health, staleThreshold, staleAction, System::currentTimeMillis);
    }

    StaleWatchdog(
            ScheduledExecutorService scheduler,
            SessionHealth health,
            Duration staleThreshold,
            Consumer<String> staleAction,
            LongSupplier clock
    ) {
        if (staleThreshold.isNegative() || staleThreshold.isZero()) {
            throw new IllegalArgumentException("staleThreshold must be positive");
        }
        this.health = health;
        this.staleThresholdMillis = staleThreshold.toMillis();
        this.staleAction = staleAction;
        this.clock = clock;
        long intervalMillis = Math.max(50L, Math.min(1_000L, staleThresholdMillis / 4L));
        this.task = scheduler.scheduleAtFixedRate(
                this::check,
                intervalMillis,
                intervalMillis,
                TimeUnit.MILLISECONDS
        );
    }

    public void check() {
        long now = clock.getAsLong();
        SessionHealthSnapshot snapshot = health.snapshot(now, staleThresholdMillis);
        if (snapshot.transportState() != TransportState.CONNECTED
                || snapshot.sessionState() == SessionState.STOPPED
                || snapshot.messageAgeMillis() < staleThresholdMillis) {
            return;
        }
        String reason = "no messages for " + snapshot.messageAgeMillis()
                + "ms; threshold=" + staleThresholdMillis + "ms";
        if (health.markStale(now, reason)) {
            staleAction.accept(reason);
        }
    }

    @Override
    public void close() {
        task.cancel(false);
    }
}

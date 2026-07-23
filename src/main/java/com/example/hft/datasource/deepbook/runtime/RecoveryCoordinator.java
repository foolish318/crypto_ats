package com.example.hft.datasource.deepbook.runtime;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.DoubleSupplier;
import java.util.function.LongConsumer;

public final class RecoveryCoordinator implements AutoCloseable {
    private static final long INITIAL_BACKOFF_MILLIS = 300L;
    private static final long MAX_BACKOFF_MILLIS = 30_000L;

    private final ScheduledExecutorService scheduler;
    private final SessionHealth health;
    private final LongConsumer reconnectAction;
    private final DoubleSupplier jitter;

    private ScheduledFuture<?> scheduled;
    private boolean stopped;
    private boolean recoveryActive;
    private boolean attemptInFlight;
    private int backoffExponent;
    private long reconnectAttempts;
    private long reconnectSuccesses;
    private long reconnectFailures;
    private long recoveryStartedNanos;
    private long recoveryDurationMillis;
    private long nextBackoffMillis;
    private String recoveryReason = "";

    public RecoveryCoordinator(
            ScheduledExecutorService scheduler,
            SessionHealth health,
            LongConsumer reconnectAction
    ) {
        this(
                scheduler,
                health,
                reconnectAction,
                () -> ThreadLocalRandom.current().nextDouble()
        );
    }

    RecoveryCoordinator(
            ScheduledExecutorService scheduler,
            SessionHealth health,
            LongConsumer reconnectAction,
            DoubleSupplier jitter
    ) {
        this.scheduler = scheduler;
        this.health = health;
        this.reconnectAction = reconnectAction;
        this.jitter = jitter;
    }

    public synchronized boolean requestRecovery(String reason) {
        if (stopped || scheduled != null) {
            return false;
        }
        attemptInFlight = false;
        recoveryReason = reason == null ? "" : reason;
        health.recovering(recoveryReason);
        if (!recoveryActive) {
            recoveryActive = true;
            recoveryStartedNanos = System.nanoTime();
        } else {
            reconnectFailures++;
            backoffExponent = Math.min(backoffExponent + 1, 30);
        }
        nextBackoffMillis = calculateDelayMillis(backoffExponent, jitter.getAsDouble());
        scheduled = scheduler.schedule(
                this::runReconnectAttempt,
                nextBackoffMillis,
                TimeUnit.MILLISECONDS
        );
        return true;
    }

    public synchronized void connectEstablished() {
        attemptInFlight = false;
        if (recoveryActive) {
            reconnectSuccesses++;
        }
    }

    public void connectFailed(String reason) {
        synchronized (this) {
            if (stopped) {
                return;
            }
            attemptInFlight = false;
        }
        requestRecovery(reason);
    }

    public synchronized void recoveredLive() {
        if (!recoveryActive) {
            return;
        }
        recoveryDurationMillis = TimeUnit.NANOSECONDS.toMillis(
                System.nanoTime() - recoveryStartedNanos);
        recoveryActive = false;
        backoffExponent = 0;
        nextBackoffMillis = 0L;
        recoveryReason = "";
    }

    public synchronized RecoverySnapshot snapshot() {
        long durationMillis = recoveryActive
                ? TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - recoveryStartedNanos)
                : recoveryDurationMillis;
        return new RecoverySnapshot(
                reconnectAttempts,
                reconnectSuccesses,
                reconnectFailures,
                durationMillis,
                nextBackoffMillis,
                recoveryReason,
                scheduled != null || attemptInFlight
        );
    }

    public synchronized boolean stopped() {
        return stopped;
    }

    @Override
    public synchronized void close() {
        stopped = true;
        attemptInFlight = false;
        if (scheduled != null) {
            scheduled.cancel(false);
            scheduled = null;
        }
    }

    static long calculateDelayMillis(int exponent, double jitterValue) {
        int boundedExponent = Math.max(0, Math.min(exponent, 30));
        long multiplier = 1L << Math.min(boundedExponent, 16);
        long base = Math.min(MAX_BACKOFF_MILLIS, INITIAL_BACKOFF_MILLIS * multiplier);
        double boundedJitter = Math.max(0.0, Math.min(1.0, jitterValue));
        double factor = 0.8 + (boundedJitter * 0.4);
        return Math.min(MAX_BACKOFF_MILLIS, Math.max(1L, Math.round(base * factor)));
    }

    private void runReconnectAttempt() {
        long attempt;
        synchronized (this) {
            scheduled = null;
            if (stopped) {
                return;
            }
            reconnectAttempts++;
            attemptInFlight = true;
            attempt = reconnectAttempts;
        }
        try {
            reconnectAction.accept(attempt);
        } catch (Throwable error) {
            connectFailed(error.getClass().getSimpleName() + ": " + error.getMessage());
        }
    }
}

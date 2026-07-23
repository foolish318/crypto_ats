package com.example.hft.datasource.deepbook.runtime;

import com.example.hft.datasource.deepbook.DeepBookSourceDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;


public final class VenueSessionProtocol implements VenueProtocolStateMachine {
    private static final Duration DEFAULT_ACK_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration BINANCE_MAX_CONNECTION_AGE = Duration.ofHours(23)
            .plusMinutes(55);

    private final DeepBookSourceDefinition source;
    private final VenueProtocolMessageClassifier classifier;
    private final LongConsumer pingSender;
    private final Consumer<String> recoveryAction;
    private final LongSupplier clock;
    private final long pingAfterMillis;
    private final long pongTimeoutMillis;
    private final long ackTimeoutMillis;
    private final long maxConnectionAgeMillis;
    private final ScheduledFuture<?> task;

    private boolean connected;
    private boolean stopped;
    private boolean subscriptionAcknowledged;
    private boolean awaitingPong;
    private long generation;
    private long connectedAtMillis;
    private long lastInboundMillis;
    private long lastPingMillis;
    private long controlMessages;
    private long subscriptionAcks;
    private long subscriptionErrors;
    private long heartbeats;
    private long pingsSent;
    private long pongsReceived;
    private long protocolErrors;

    public VenueSessionProtocol(
            ScheduledExecutorService scheduler,
            DeepBookSourceDefinition source,
            Duration staleThreshold,
            LongConsumer pingSender,
            Consumer<String> recoveryAction
    ) {
        this(
                scheduler,
                source,
                boundedPingAfter(staleThreshold),
                boundedPongTimeout(staleThreshold),
                DEFAULT_ACK_TIMEOUT,
                "BINANCE_US".equals(source.exchange())
                        ? BINANCE_MAX_CONNECTION_AGE
                        : Duration.ZERO,
                pingSender,
                recoveryAction,
                System::currentTimeMillis,
                new ObjectMapper()
        );
    }

    VenueSessionProtocol(
            ScheduledExecutorService scheduler,
            DeepBookSourceDefinition source,
            Duration pingAfter,
            Duration pongTimeout,
            Duration ackTimeout,
            Duration maxConnectionAge,
            LongConsumer pingSender,
            Consumer<String> recoveryAction,
            LongSupplier clock,
            ObjectMapper mapper
    ) {
        this.source = source;
        this.classifier = new VenueProtocolMessageClassifier(source, mapper);
        this.pingSender = pingSender;
        this.recoveryAction = recoveryAction;
        this.clock = clock;
        this.pingAfterMillis = positiveMillis(pingAfter, "pingAfter");
        this.pongTimeoutMillis = positiveMillis(pongTimeout, "pongTimeout");
        this.ackTimeoutMillis = positiveMillis(ackTimeout, "ackTimeout");
        this.maxConnectionAgeMillis = maxConnectionAge.isZero()
                ? 0L
                : positiveMillis(maxConnectionAge, "maxConnectionAge");
        long interval = Math.max(50L, Math.min(1_000L, pingAfterMillis / 4L));
        this.task = scheduler.scheduleAtFixedRate(
                this::check,
                interval,
                interval,
                TimeUnit.MILLISECONDS
        );
    }

    public synchronized void connected(long nextGeneration) {
        if (stopped) {
            return;
        }
        generation = nextGeneration;
        connected = true;
        connectedAtMillis = clock.getAsLong();
        lastInboundMillis = connectedAtMillis;
        lastPingMillis = 0L;
        awaitingPong = false;
        subscriptionAcknowledged = !source.hasSubscribeMessage();
    }

    public synchronized ProtocolMessageDecision onText(String payload) {
        ProtocolMessageDecision decision = classifier.classify(payload);
        lastInboundMillis = clock.getAsLong();
        awaitingPong = false;
        if (!decision.bookData()) {
            controlMessages++;
        }
        switch (decision.type()) {
            case SUBSCRIPTION_ACK -> {
                subscriptionAcknowledged = true;
                subscriptionAcks++;
            }
            case HEARTBEAT -> heartbeats++;
            case PONG -> pongsReceived++;
            case ERROR -> {
                protocolErrors++;
                if (!subscriptionAcknowledged) {
                    subscriptionErrors++;
                }
            }
            default -> {
            }
        }
        return decision;
    }

    public synchronized void onProtocolPing() {
        lastInboundMillis = clock.getAsLong();
        awaitingPong = false;
        heartbeats++;
        controlMessages++;
    }

    public synchronized void onProtocolPong() {
        lastInboundMillis = clock.getAsLong();
        awaitingPong = false;
        pongsReceived++;
        controlMessages++;
    }

    public synchronized void disconnected() {
        connected = false;
        awaitingPong = false;
    }

    public synchronized boolean bookDataAllowed() {
        return connected && subscriptionAcknowledged && !stopped;
    }

    public synchronized ProtocolSnapshot snapshot() {
        return new ProtocolSnapshot(
                controlMessages,
                subscriptionAcks,
                subscriptionErrors,
                heartbeats,
                pingsSent,
                pongsReceived,
                protocolErrors,
                subscriptionAcknowledged,
                awaitingPong
        );
    }

    public void check() {
        String failure = null;
        long pingGeneration = 0L;
        synchronized (this) {
            if (stopped || !connected) {
                return;
            }
            long now = clock.getAsLong();
            if (!subscriptionAcknowledged
                    && now - connectedAtMillis >= ackTimeoutMillis) {
                connected = false;
                subscriptionErrors++;
                protocolErrors++;
                failure = "subscription ACK timeout after " + ackTimeoutMillis + "ms";
            } else if (maxConnectionAgeMillis > 0L
                    && now - connectedAtMillis >= maxConnectionAgeMillis) {
                connected = false;
                failure = "proactive reconnect at venue connection age "
                        + (now - connectedAtMillis) + "ms";
            } else if (awaitingPong
                    && now - lastPingMillis >= pongTimeoutMillis) {
                connected = false;
                protocolErrors++;
                failure = "pong timeout after " + (now - lastPingMillis) + "ms";
            } else if (!awaitingPong
                    && now - lastInboundMillis >= pingAfterMillis) {
                awaitingPong = true;
                lastPingMillis = now;
                pingsSent++;
                pingGeneration = generation;
            }
        }
        if (failure != null) {
            recoveryAction.accept(failure);
        } else if (pingGeneration != 0L) {
            try {
                pingSender.accept(pingGeneration);
            } catch (Throwable error) {
                recoveryAction.accept(
                        "heartbeat send failed: " + error.getClass().getSimpleName()
                                + ": " + error.getMessage());
            }
        }
    }

    @Override
    public synchronized void close() {
        stopped = true;
        connected = false;
        awaitingPong = false;
        task.cancel(false);
    }

    private static Duration boundedPingAfter(Duration staleThreshold) {
        long millis = Math.max(500L, Math.min(20_000L, staleThreshold.toMillis() / 2L));
        return Duration.ofMillis(millis);
    }

    private static Duration boundedPongTimeout(Duration staleThreshold) {
        long millis = Math.max(500L, Math.min(10_000L, staleThreshold.toMillis() / 2L));
        return Duration.ofMillis(millis);
    }

    private static long positiveMillis(Duration duration, String label) {
        if (duration.isNegative() || duration.isZero()) {
            throw new IllegalArgumentException(label + " must be positive");
        }
        return duration.toMillis();
    }
}
package com.example.hft.app;

import com.example.hft.datasource.DataSourceModuleVersion;
import com.example.hft.datasource.deepbook.DeepBookSourceCatalog;
import com.example.hft.datasource.deepbook.DeepBookSourceDefinition;
import com.example.hft.datasource.deepbook.runtime.AcceptedBookEventRecorder;
import com.example.hft.datasource.deepbook.runtime.AsyncRawRecorder;
import com.example.hft.datasource.deepbook.runtime.CrossExchangeBookView;
import com.example.hft.datasource.deepbook.runtime.DeepBookStrategyListener;
import com.example.hft.datasource.deepbook.runtime.LiveBookSession;
import com.example.hft.datasource.deepbook.runtime.LiveBookSessionSnapshot;
import com.example.hft.datasource.deepbook.runtime.LiveBookSessionFinalState;
import com.example.hft.datasource.deepbook.runtime.LocalBookPublisher;
import com.example.hft.datasource.deepbook.runtime.LocalBookSnapshot;
import com.example.hft.datasource.deepbook.runtime.LocalOrderBookBuilderFactory;
import com.example.hft.datasource.deepbook.runtime.RawRecorderSummary;
import com.example.hft.datasource.deepbook.runtime.RawReplayProcessor;
import com.example.hft.datasource.deepbook.runtime.RawReplayResult;
import com.example.hft.datasource.engine.MarketDataCache;
import com.example.hft.datasource.engine.MarketDataEngine;
import com.example.hft.datasource.engine.MarketDataEventBus;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


public final class MultiExchangeLocalBookMain {
    private static final int DEFAULT_DURATION_SECONDS = 15;
    private static final int DEFAULT_STALE_THRESHOLD_SECONDS = 10;
    private static final int PUBLISHED_DEPTH = 10;
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(10);

    private MultiExchangeLocalBookMain() {
    }

    public static void main(String[] args) throws Exception {
        int durationSeconds = args.length > 0
                ? positiveInt(args[0], "durationSeconds")
                : DEFAULT_DURATION_SECONDS;
        Path outputDir = args.length > 1 ? Path.of(args[1]) : Path.of("data");
        int staleThresholdSeconds = args.length > 2
                ? positiveInt(args[2], "staleThresholdSeconds")
                : DEFAULT_STALE_THRESHOLD_SECONDS;
        Files.createDirectories(outputDir);

        String runId = runId();
        Path rawFile = outputDir.resolve("multi-exchange-raw-v21-" + runId + ".jsonl");
        Path summaryFile = outputDir.resolve("multi-exchange-books-v21-" + runId + ".json");
        ObjectMapper mapper = new ObjectMapper();
        List<DeepBookSourceDefinition> sources = DeepBookSourceCatalog.defaultSources();

        MarketDataCache cache = new MarketDataCache();
        MarketDataEventBus eventBus = new MarketDataEventBus();
        MarketDataEngine engine = new MarketDataEngine(cache, eventBus);
        AcceptedBookEventRecorder acceptedRecorder = new AcceptedBookEventRecorder();
        CrossExchangeBookView crossExchangeView = new CrossExchangeBookView();
        DeepBookStrategyListener strategy = new DeepBookStrategyListener();
        eventBus.subscribe(acceptedRecorder);
        eventBus.subscribe(crossExchangeView);
        eventBus.subscribe(strategy);

        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(8);
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(HTTP_TIMEOUT)
                .build();
        Duration staleThreshold = Duration.ofSeconds(staleThresholdSeconds);
        LocalBookPublisher publisher = new LocalBookPublisher(
                engine,
                staleThreshold.toMillis(),
                PUBLISHED_DEPTH
        );
        AsyncRawRecorder rawRecorder = new AsyncRawRecorder(rawFile, mapper);
        List<LiveBookSession> sessions = new ArrayList<>();
        List<LiveBookSessionSnapshot> runEndSessions = List.of();
        Map<String, LocalBookSnapshot> runEndBooks = Map.of();
        long startedNanos = System.nanoTime();

        try {
            for (DeepBookSourceDefinition source : sources) {
                LiveBookSession session = new LiveBookSession(
                        source,
                        LocalOrderBookBuilderFactory.create(source),
                        httpClient,
                        scheduler,
                        rawRecorder,
                        publisher,
                        staleThreshold
                );
                sessions.add(session);
                session.start();
            }
            Thread.sleep(Duration.ofSeconds(durationSeconds).toMillis());
            List<LiveBookSessionSnapshot> capturedSessions = new ArrayList<>();
            Map<String, LocalBookSnapshot> snapshots = new LinkedHashMap<>();
            for (LiveBookSession session : sessions) {
                LiveBookSessionFinalState captured = session.stopAndSnapshot(PUBLISHED_DEPTH);
                capturedSessions.add(captured.session());
                snapshots.put(captured.book().sourceId(), captured.book());
            }
            runEndSessions = List.copyOf(capturedSessions);
            runEndBooks = Map.copyOf(snapshots);
        } finally {
            sessions.forEach(LiveBookSession::close);
            rawRecorder.awaitDrained(10_000L);
            rawRecorder.close();
            scheduler.shutdownNow();
        }

        long elapsedNanos = System.nanoTime() - startedNanos;
        RawRecorderSummary recorderSummary = rawRecorder.summary();
        ReplayCheck replayCheck = verifyReplay(
                recorderSummary,
                rawFile,
                sources,
                runEndBooks,
                mapper
        );
        List<LiveBookSessionSnapshot> stoppedSessions =
                sessions.stream().map(LiveBookSession::snapshot).toList();
        writeSummary(
                summaryFile,
                mapper,
                runEndSessions,
                stoppedSessions,
                recorderSummary,
                replayCheck,
                durationSeconds,
                staleThresholdSeconds,
                elapsedNanos,
                rawFile,
                cache,
                eventBus,
                acceptedRecorder,
                crossExchangeView,
                strategy
        );
        printSummary(
                runEndSessions,
                stoppedSessions,
                recorderSummary,
                replayCheck,
                durationSeconds,
                staleThresholdSeconds,
                elapsedNanos,
                rawFile,
                summaryFile,
                cache,
                acceptedRecorder,
                crossExchangeView,
                strategy
        );
    }

    private static ReplayCheck verifyReplay(
            RawRecorderSummary recorderSummary,
            Path rawFile,
            List<DeepBookSourceDefinition> sources,
            Map<String, LocalBookSnapshot> liveBooks,
            ObjectMapper mapper
    ) {
        if (!recorderSummary.replaySafe()) {
            return new ReplayCheck(false, 0L, 0L, "raw recorder marked file replay-unsafe");
        }
        try {
            RawReplayResult replay = new RawReplayProcessor(sources, PUBLISHED_DEPTH, mapper)
                    .replay(rawFile);
            for (Map.Entry<String, LocalBookSnapshot> entry : liveBooks.entrySet()) {
                LocalBookSnapshot replayed = replay.book(entry.getKey()).orElse(null);
                if (!sameBook(entry.getValue(), replayed)) {
                    return new ReplayCheck(
                            false,
                            replay.appliedRecords(),
                            replay.ignoredRecords(),
                            "final book mismatch for " + entry.getKey()
                    );
                }
            }
            return new ReplayCheck(
                    true,
                    replay.appliedRecords(),
                    replay.ignoredRecords(),
                    ""
            );
        } catch (Exception e) {
            return new ReplayCheck(
                    false,
                    0L,
                    0L,
                    e.getClass().getSimpleName() + ": " + e.getMessage()
            );
        }
    }

    private static boolean sameBook(LocalBookSnapshot live, LocalBookSnapshot replayed) {
        return replayed != null
                && live.sequence() == replayed.sequence()
                && live.quality() == replayed.quality()
                && live.bids().equals(replayed.bids())
                && live.asks().equals(replayed.asks());
    }

    private static void writeSummary(
            Path summaryFile,
            ObjectMapper mapper,
            List<LiveBookSessionSnapshot> runEndSessions,
            List<LiveBookSessionSnapshot> stoppedSessions,
            RawRecorderSummary recorder,
            ReplayCheck replay,
            int durationSeconds,
            int staleThresholdSeconds,
            long elapsedNanos,
            Path rawFile,
            MarketDataCache cache,
            MarketDataEventBus eventBus,
            AcceptedBookEventRecorder acceptedRecorder,
            CrossExchangeBookView crossExchangeView,
            DeepBookStrategyListener strategy
    ) throws Exception {
        ObjectNode root = mapper.createObjectNode();
        root.put("version", DataSourceModuleVersion.VERSION);
        root.put("durationSeconds", durationSeconds);
        root.put("staleThresholdSeconds", staleThresholdSeconds);
        root.put("elapsedMillis", TimeUnit.NANOSECONDS.toMillis(elapsedNanos));
        root.put("rawFile", rawFile.toString());
        root.put("publishableBooksAtRunEnd",
                runEndSessions.stream().filter(item -> item.health().publishable(
                        TimeUnit.SECONDS.toMillis(staleThresholdSeconds))).count());
        root.put("deepBookCacheEntries", cache.deepBookCount());
        root.put("eventBusListeners", eventBus.listenerCount());
        root.put("acceptedEventsRecorded", acceptedRecorder.recorded());
        root.put("crossExchangeBooks", crossExchangeView.size());
        root.put("strategyAcceptedBooks", strategy.acceptedBooks());
        root.put("strategyUsableBooks", strategy.usableBooks());
        addRecorder(root, recorder);
        root.put("replayParity", replay.parity());
        root.put("replayAppliedRecords", replay.appliedRecords());
        root.put("replayIgnoredRecords", replay.ignoredRecords());
        root.put("replayFailure", replay.failure());

        Map<String, LiveBookSessionSnapshot> stoppedBySource = new LinkedHashMap<>();
        stoppedSessions.forEach(item -> stoppedBySource.put(item.sourceId(), item));
        ArrayNode sourceNodes = root.putArray("sources");
        for (LiveBookSessionSnapshot session : runEndSessions) {
            ObjectNode node = sourceNodes.addObject();
            addSession(node, session);
            LiveBookSessionSnapshot stopped = stoppedBySource.get(session.sourceId());
            node.put("finalTransportState", stopped.health().transportState().name());
            node.put("finalSessionState", stopped.health().sessionState().name());
        }
        Files.writeString(
                summaryFile,
                mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root),
                StandardCharsets.UTF_8
        );
    }

    private static void addRecorder(ObjectNode root, RawRecorderSummary recorder) {
        root.put("recordedRecords", recorder.recordedRecords());
        root.put("droppedRecords", recorder.droppedRecords());
        root.put("replaySafe", recorder.replaySafe());
        root.put("firstDropEpochMillis", recorder.firstDropEpochMillis());
        root.put("firstDropReason", recorder.firstDropReason());
        root.put("recorderFailure", recorder.failure());
    }

    private static void addSession(ObjectNode node, LiveBookSessionSnapshot session) {
        node.put("sourceId", session.sourceId());
        node.put("exchange", session.exchange());
        node.put("symbol", session.symbol());
        node.put("generation", session.generation());
        node.put("transportState", session.health().transportState().name());
        node.put("bookState", session.health().bookState().name());
        node.put("sessionState", session.health().sessionState().name());
        node.put("lastMessageTime", session.health().lastMessageTime());
        node.put("lastAcceptedTime", session.health().lastAcceptedTime());
        node.put("messageAgeMillis", session.health().messageAgeMillis());
        node.put("staleTransitions", session.health().staleTransitions());
        node.put("recoveryReason", session.health().recoveryReason());
        node.put("sequence", session.sequence());
        node.put("messages", session.messages());
        node.put("accepted", session.accepted());
        node.put("snapshots", session.snapshots());
        node.put("appliedUpdates", session.appliedUpdates());
        node.put("rejected", session.rejected());
        node.put("staleUpdates", session.staleUpdates());
        node.put("ignored", session.ignored());
        node.put("published", session.published());
        node.put("parseAvgMicros", session.parseAvgMicros());
        node.put("bookAvgMicros", session.bookAvgMicros());
        node.put("reconnectAttempts", session.recovery().reconnectAttempts());
        node.put("reconnectSuccesses", session.recovery().reconnectSuccesses());
        node.put("reconnectFailures", session.recovery().reconnectFailures());
        node.put("recoveryDurationMillis", session.recovery().recoveryDurationMillis());
        node.put("bestBid", session.bestBid());
        node.put("bestAsk", session.bestAsk());
        node.put("lastFailure", session.lastFailure());
    }

    private static void printSummary(
            List<LiveBookSessionSnapshot> runEndSessions,
            List<LiveBookSessionSnapshot> stoppedSessions,
            RawRecorderSummary recorder,
            ReplayCheck replay,
            int durationSeconds,
            int staleThresholdSeconds,
            long elapsedNanos,
            Path rawFile,
            Path summaryFile,
            MarketDataCache cache,
            AcceptedBookEventRecorder acceptedRecorder,
            CrossExchangeBookView crossExchangeView,
            DeepBookStrategyListener strategy
    ) {
        long publishableBooks = runEndSessions.stream()
                .filter(item -> item.health().publishable(
                        TimeUnit.SECONDS.toMillis(staleThresholdSeconds)))
                .count();
        System.out.println("MULTI_EXCHANGE_LOCAL_BOOK_SUMMARY"
                + " version=" + DataSourceModuleVersion.VERSION
                + " durationSeconds=" + durationSeconds
                + " staleThresholdSeconds=" + staleThresholdSeconds
                + " elapsedMs=" + TimeUnit.NANOSECONDS.toMillis(elapsedNanos)
                + " sources=" + runEndSessions.size()
                + " publishableBooks=" + publishableBooks
                + " messages=" + runEndSessions.stream()
                        .mapToLong(LiveBookSessionSnapshot::messages).sum()
                + " published=" + runEndSessions.stream()
                        .mapToLong(LiveBookSessionSnapshot::published).sum()
                + " rejected=" + runEndSessions.stream()
                        .mapToLong(LiveBookSessionSnapshot::rejected).sum()
                + " reconnectAttempts=" + runEndSessions.stream()
                        .mapToLong(item -> item.recovery().reconnectAttempts()).sum()
                + " deepBookCache=" + cache.deepBookCount()
                + " eventRecorder=" + acceptedRecorder.recorded()
                + " crossExchangeView=" + crossExchangeView.size()
                + " strategyEvents=" + strategy.acceptedBooks()
                + " recordedRecords=" + recorder.recordedRecords()
                + " droppedRecords=" + recorder.droppedRecords()
                + " replaySafe=" + recorder.replaySafe()
                + " replayParity=" + replay.parity());

        Map<String, LiveBookSessionSnapshot> stoppedBySource = new LinkedHashMap<>();
        stoppedSessions.forEach(item -> stoppedBySource.put(item.sourceId(), item));
        for (LiveBookSessionSnapshot session : runEndSessions) {
            LiveBookSessionSnapshot stopped = stoppedBySource.get(session.sourceId());
            System.out.println("BOOK source=" + session.sourceId()
                    + " transport=" + session.health().transportState()
                    + " book=" + session.health().bookState()
                    + " session=" + session.health().sessionState()
                    + " finalSession=" + stopped.health().sessionState()
                    + " generation=" + session.generation()
                    + " messages=" + session.messages()
                    + " published=" + session.published()
                    + " rejected=" + session.rejected()
                    + " messageAgeMs=" + session.health().messageAgeMillis()
                    + " staleTransitions=" + session.health().staleTransitions()
                    + " reconnectAttempts=" + session.recovery().reconnectAttempts()
                    + " reconnectSuccesses=" + session.recovery().reconnectSuccesses()
                    + " reconnectFailures=" + session.recovery().reconnectFailures()
                    + " recoveryDurationMs=" + session.recovery().recoveryDurationMillis()
                    + " parseAvgUs=" + format(session.parseAvgMicros())
                    + " bookAvgUs=" + format(session.bookAvgMicros())
                    + " bid1=" + session.bestBid()
                    + " ask1=" + session.bestAsk()
                    + (session.health().recoveryReason().isBlank()
                    ? ""
                    : " recoveryReason=" + session.health().recoveryReason()));
        }
        System.out.println("FILES raw=" + rawFile
                + " summary=" + summaryFile
                + (replay.failure().isBlank() ? "" : " replayFailure=" + replay.failure()));
    }

    private static int positiveInt(String value, String label) {
        int parsed = Integer.parseInt(value);
        if (parsed <= 0) {
            throw new IllegalArgumentException(label + " must be positive");
        }
        return parsed;
    }

    private static String runId() {
        return Instant.now().toString().replace(":", "").replace(".", "");
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private record ReplayCheck(
            boolean parity,
            long appliedRecords,
            long ignoredRecords,
            String failure
    ) {
    }
}

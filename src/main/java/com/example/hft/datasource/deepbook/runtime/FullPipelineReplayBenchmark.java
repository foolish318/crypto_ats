package com.example.hft.datasource.deepbook.runtime;

import com.example.hft.datasource.book.BookQuality;
import com.example.hft.datasource.deepbook.DeepBookSourceDefinition;
import com.example.hft.datasource.engine.AsyncListenerSnapshot;
import com.example.hft.datasource.engine.MarketDataCache;
import com.example.hft.datasource.engine.MarketDataEngine;
import com.example.hft.datasource.engine.MarketDataEventBus;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;


public final class FullPipelineReplayBenchmark {
    private static final int SNAPSHOT_LEVELS = 10;
    private static final long MAX_BOOTSTRAP_BYTES = 64L * 1024L * 1024L;

    private final List<DeepBookSourceDefinition> sources;
    private final Map<String, DeepBookSourceDefinition> sourceById = new LinkedHashMap<>();
    private final List<RawEnvelope> records;
    private final ObjectMapper mapper;

    public FullPipelineReplayBenchmark(
            List<DeepBookSourceDefinition> sources,
            List<RawEnvelope> records,
            ObjectMapper mapper
    ) {
        this.sources = List.copyOf(sources);
        this.records = List.copyOf(records);
        this.mapper = mapper;
        for (DeepBookSourceDefinition source : sources) {
            sourceById.put(source.id(), source);
        }
        if (records.isEmpty()) {
            throw new IllegalArgumentException("full pipeline benchmark requires records");
        }
    }

    public FullPipelineBenchmarkResult run(Path outputJournal) throws Exception {
        AtomicLong clock = new AtomicLong(records.get(0).receivedEpochMillis());
        MarketDataCache cache = new MarketDataCache();
        MarketDataEventBus bus = new MarketDataEventBus();
        MarketDataEngine engine = new MarketDataEngine(cache, bus);
        DeepBookStrategyListener strategy = new DeepBookStrategyListener();
        CrossExchangeBookView view = new CrossExchangeBookView(
                Duration.ofMinutes(1),
                Duration.ofSeconds(1),
                clock::get
        );
        AcceptedBookEventRecorder sideRecorder = new AcceptedBookEventRecorder();
        bus.subscribe(view);
        bus.subscribe(strategy);
        bus.subscribeAsync("benchmark-recorder", sideRecorder, 8_192);
        LocalBookPublisher publisher = new LocalBookPublisher(
                engine,
                Duration.ofMinutes(1).toMillis(),
                SNAPSHOT_LEVELS,
                clock::get
        );
        AsyncRawRecorder rawRecorder = new AsyncRawRecorder(
                outputJournal,
                mapper,
                65_536,
                RawJournalConfig.defaults()
        );
        Map<String, PipelineState> states = new LinkedHashMap<>();
        StageSamples samples = new StageSamples(expectedIntervalNanos(records));
        GcSnapshot gcBefore = GcSnapshot.capture();
        long allocatedBefore = allocatedBytes();
        long started = System.nanoTime();
        long rejected = 0L;

        for (RawEnvelope record : records) {
            clock.set(record.receivedEpochMillis());
            long ingressStarted = System.nanoTime();
            boolean recorded = rawRecorder.record(record);
            samples.ingress.add(System.nanoTime() - ingressStarted);
            if (!recorded) {
                rejected++;
            }
            rejected += process(record, ingressStarted, states, publisher, samples);
        }
        long elapsedNanos = System.nanoTime() - started;
        long allocatedAfter = allocatedBytes();
        bus.close();
        rawRecorder.close();
        GcSnapshot gcAfter = GcSnapshot.capture();

        RawReplayResult expected = new RawReplayProcessor(
                sources,
                SNAPSHOT_LEVELS,
                mapper
        ).replay(records);
        Map<String, LocalBookSnapshot> actualBooks = new LinkedHashMap<>();
        states.forEach((sourceId, state) ->
                actualBooks.put(sourceId, state.pipeline.snapshot(SNAPSHOT_LEVELS)));
        RawReplayResult actual = new RawReplayResult(actualBooks, 0L, 0L);
        boolean parity = sameBooks(expected, actual);
        RawRecorderSummary recorderSummary = rawRecorder.summary();
        List<AsyncListenerSnapshot> asyncSnapshots = bus.asyncSnapshots();
        long asyncDrops = asyncSnapshots.stream()
                .mapToLong(AsyncListenerSnapshot::droppedEvents)
                .sum();
        long dropped = recorderSummary.droppedRecords() + asyncDrops;

        long allocationDelta = allocatedBefore < 0L || allocatedAfter < 0L
                ? -1L
                : Math.max(0L, allocatedAfter - allocatedBefore);
        double allocationPerMessage = allocationDelta < 0L
                ? -1.0
                : (double) allocationDelta / records.size();
        Map<String, PipelineLatencyDistribution> stages = new LinkedHashMap<>();
        stages.put("ingressRecorderOffer", samples.ingress.distribution());
        stages.put("protocol", samples.protocol.distribution());
        stages.put("parse", samples.parse.distribution());
        stages.put("bookMutation", samples.book.distribution());
        stages.put("snapshot", samples.snapshot.distribution());
        stages.put("cache", samples.cache.distribution());
        stages.put("coreListeners", samples.listeners.distribution());
        stages.put("asyncListenerOffer", samples.asyncOffer.distribution());
        stages.put("publishTotal", samples.publish.distribution());
        stages.put("endToEndCorrected", samples.endToEnd.distribution());

        return new FullPipelineBenchmarkResult(
                records.size(),
                elapsedNanos / 1_000_000.0,
                records.size() * 1_000_000_000.0 / elapsedNanos,
                allocationPerMessage,
                Math.max(0L, gcAfter.count - gcBefore.count),
                Math.max(0L, gcAfter.timeMillis - gcBefore.timeMillis),
                stages,
                samples.bootstrap.distribution(),
                samples.incremental.distribution(),
                rejected,
                dropped,
                parity,
                recorderSummary,
                asyncSnapshots,
                System.getProperty("java.version"),
                System.getProperty("java.vm.name"),
                Runtime.getRuntime().availableProcessors(),
                String.join(" ", ManagementFactory.getRuntimeMXBean().getInputArguments())
        );
    }

    private long process(
            RawEnvelope record,
            long ingressStarted,
            Map<String, PipelineState> states,
            LocalBookPublisher publisher,
            StageSamples samples
    ) {
        DeepBookSourceDefinition source = sourceById.get(record.sourceId());
        if (source == null) {
            samples.endToEnd.addCorrected(System.nanoTime() - ingressStarted);
            return 1L;
        }
        if (record.recordType() == RawRecordType.CONNECT) {
            PipelineState current = states.get(record.sourceId());
            if (current == null || current.generation != record.generation()) {
                current = new PipelineState(source, record.generation(), publisher, mapper);
                states.put(record.sourceId(), current);
            }
            current.active = true;
            current.health.connecting(record.generation() > 1L);
            current.health.connected(record.receivedEpochMillis());
            samples.endToEnd.addCorrected(System.nanoTime() - ingressStarted);
            return 0L;
        }
        PipelineState state = states.get(record.sourceId());
        if (state == null || state.generation != record.generation()) {
            samples.endToEnd.addCorrected(System.nanoTime() - ingressStarted);
            return 1L;
        }
        if (record.recordType() == RawRecordType.DISCONNECT
                || record.recordType() == RawRecordType.RECOVERY) {
            state.active = false;
            state.health.recovering(record.detail());
            state.pipeline.availability(
                    state.generation,
                    BookAvailabilityState.RECOVERING,
                    record.detail()
            );
            samples.endToEnd.addCorrected(System.nanoTime() - ingressStarted);
            return 0L;
        }
        if (!state.active) {
            samples.endToEnd.addCorrected(System.nanoTime() - ingressStarted);
            return 1L;
        }
        if (record.recordType() == RawRecordType.REST_SNAPSHOT
                && record.detail().startsWith("BEFORE_APPLY")) {
            BookUpdateResult result = state.pipeline.loadSnapshot(
                    record.payload(),
                    record.receivedEpochMillis()
            );
            long rejected = applyResult(
                    record, ingressStarted, state, result, samples, true
            );
            PendingRecord pending;
            while ((pending = state.pending.poll()) != null) {
                BookUpdateResult update = state.pipeline.onMessage(
                        pending.record.payload(),
                        pending.record.receivedEpochMillis()
                );
                rejected += applyResult(
                        pending.record,
                        pending.ingressStarted,
                        state,
                        update,
                        samples,
                        false
                );
            }
            return rejected;
        }
        if (record.recordType() != RawRecordType.WS_MESSAGE
                || record.detail().startsWith("CONTROL ")) {
            samples.endToEnd.addCorrected(System.nanoTime() - ingressStarted);
            return 0L;
        }

        state.health.messageReceived(record.receivedEpochMillis());
        long protocolStarted = System.nanoTime();
        ProtocolMessageDecision decision = state.classifier.classify(record.payload());
        samples.protocol.add(System.nanoTime() - protocolStarted);
        if (decision.type() == ProtocolMessageType.SUBSCRIPTION_ACK) {
            state.subscriptionAcknowledged = true;
        }
        if (decision.fatal()) {
            state.active = false;
            state.pipeline.availability(
                    state.generation,
                    BookAvailabilityState.INVALID,
                    decision.detail()
            );
            samples.endToEnd.addCorrected(System.nanoTime() - ingressStarted);
            return 1L;
        }
        if (!decision.bookData()) {
            samples.endToEnd.addCorrected(System.nanoTime() - ingressStarted);
            return 0L;
        }
        if (!state.subscriptionAcknowledged) {
            samples.endToEnd.addCorrected(System.nanoTime() - ingressStarted);
            return 1L;
        }
        if ("BINANCE_US".equals(source.exchange())
                && state.pipeline.quality() == BookQuality.EMPTY) {
            if (!state.pending.offer(new PendingRecord(record, ingressStarted))) {
                state.pipeline.availability(
                        state.generation,
                        BookAvailabilityState.INVALID,
                        "benchmark bootstrap buffer overflow"
                );
                samples.endToEnd.addCorrected(System.nanoTime() - ingressStarted);
                return 1L;
            }
            return 0L;
        }
        BookUpdateResult result = state.pipeline.onMessage(
                record.payload(),
                record.receivedEpochMillis()
        );
        return applyResult(
                record,
                ingressStarted,
                state,
                result,
                samples,
                result.status() == BookUpdateStatus.SNAPSHOT_LOADED
        );
    }

    private long applyResult(
            RawEnvelope record,
            long ingressStarted,
            PipelineState state,
            BookUpdateResult result,
            StageSamples samples,
            boolean bootstrap
    ) {
        samples.parse.add(result.parseNanos());
        samples.book.add(result.bookNanos());
        state.health.bookState(BookState.from(result.quality()));
        if (!result.accepted() || result.quality() != BookQuality.LIVE) {
            if (result.requiresRecovery()) {
                state.pipeline.availability(
                        state.generation,
                        BookAvailabilityState.INVALID,
                        result.detail()
                );
            }
            samples.endToEnd.addCorrected(System.nanoTime() - ingressStarted);
            return result.status() == BookUpdateStatus.IGNORED ? 0L : 1L;
        }
        state.health.accepted(record.receivedEpochMillis());
        long publishStarted = System.nanoTime();
        BookPublishResult published = state.pipeline.publish(
                result,
                state.generation,
                record.receivedNanos(),
                record.receivedEpochMillis()
        );
        samples.publish.add(System.nanoTime() - publishStarted);
        samples.snapshot.add(published.snapshotNanos());
        samples.cache.add(published.cacheNanos());
        samples.listeners.add(published.coreListenerNanos());
        samples.asyncOffer.add(published.asyncOfferNanos());
        long latency = System.nanoTime() - ingressStarted;
        samples.endToEnd.addCorrected(latency);
        if (bootstrap) {
            samples.bootstrap.add(latency);
        } else {
            samples.incremental.add(latency);
        }
        return published.published() ? 0L : 1L;
    }

    private static boolean sameBooks(RawReplayResult expected, RawReplayResult actual) {
        try {
            DeepBookReplayBenchmark.requireSameBooks(expected, actual);
            return true;
        } catch (IllegalStateException mismatch) {
            return false;
        }
    }

    private static long allocatedBytes() {
        java.lang.management.ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        if (!(bean instanceof com.sun.management.ThreadMXBean allocationBean)
                || !allocationBean.isThreadAllocatedMemorySupported()) {
            return -1L;
        }
        if (!allocationBean.isThreadAllocatedMemoryEnabled()) {
            allocationBean.setThreadAllocatedMemoryEnabled(true);
        }
        return allocationBean.getThreadAllocatedBytes(Thread.currentThread().getId());
    }

    private static long expectedIntervalNanos(List<RawEnvelope> records) {
        long[] deltas = new long[Math.max(1, records.size() - 1)];
        int count = 0;
        long previous = records.get(0).receivedNanos();
        for (int index = 1; index < records.size(); index++) {
            long current = records.get(index).receivedNanos();
            long delta = current - previous;
            previous = current;
            if (delta > 0L && delta < Duration.ofSeconds(1).toNanos()) {
                deltas[count++] = delta;
            }
        }
        if (count == 0) {
            return 1_000_000L;
        }
        Arrays.sort(deltas, 0, count);
        return Math.max(1L, deltas[count / 2]);
    }

    private static final class PipelineState {
        private final long generation;
        private final SessionHealth health = new SessionHealth();
        private final BookPipeline pipeline;
        private final VenueProtocolMessageClassifier classifier;
        private final BoundedBootstrapBuffer<PendingRecord> pending =
                new BoundedBootstrapBuffer<>(
                        50_000,
                        MAX_BOOTSTRAP_BYTES,
                        item -> item.record.payload().length() * Character.BYTES
                );
        private boolean subscriptionAcknowledged;
        private boolean active;

        private PipelineState(
                DeepBookSourceDefinition source,
                long generation,
                LocalBookPublisher publisher,
                ObjectMapper mapper
        ) {
            this.generation = generation;
            this.pipeline = new BookPipeline(
                    LocalOrderBookBuilderFactory.create(source),
                    publisher,
                    health
            );
            this.classifier = new VenueProtocolMessageClassifier(source, mapper);
            this.subscriptionAcknowledged = !source.hasSubscribeMessage();
        }
    }

    private record PendingRecord(RawEnvelope record, long ingressStarted) {
    }

    private static final class StageSamples {
        private final Samples ingress = new Samples();
        private final Samples protocol = new Samples();
        private final Samples parse = new Samples();
        private final Samples book = new Samples();
        private final Samples snapshot = new Samples();
        private final Samples cache = new Samples();
        private final Samples listeners = new Samples();
        private final Samples asyncOffer = new Samples();
        private final Samples publish = new Samples();
        private final Samples endToEnd;
        private final Samples bootstrap = new Samples();
        private final Samples incremental = new Samples();

        private StageSamples(long expectedIntervalNanos) {
            endToEnd = new Samples(expectedIntervalNanos);
        }
    }

    private static final class Samples {
        private long[] values = new long[1_024];
        private int size;
        private long sum;
        private long max;
        private final long expectedIntervalNanos;

        private Samples() {
            this(0L);
        }

        private Samples(long expectedIntervalNanos) {
            this.expectedIntervalNanos = expectedIntervalNanos;
        }

        private void add(long value) {
            addRaw(Math.max(0L, value));
        }

        private void addCorrected(long value) {
            long bounded = Math.max(0L, value);
            addRaw(bounded);
            if (expectedIntervalNanos <= 0L) {
                return;
            }
            for (long omitted = bounded - expectedIntervalNanos;
                    omitted >= expectedIntervalNanos;
                    omitted -= expectedIntervalNanos) {
                addRaw(omitted);
            }
        }

        private void addRaw(long value) {
            if (size == values.length) {
                values = Arrays.copyOf(values, values.length * 2);
            }
            values[size++] = value;
            sum += value;
            max = Math.max(max, value);
        }

        private PipelineLatencyDistribution distribution() {
            if (size == 0) {
                return new PipelineLatencyDistribution(0L, 0, 0, 0, 0, 0, 0);
            }
            long[] sorted = Arrays.copyOf(values, size);
            Arrays.sort(sorted);
            return new PipelineLatencyDistribution(
                    size,
                    micros((double) sum / size),
                    micros(percentile(sorted, 0.50)),
                    micros(percentile(sorted, 0.95)),
                    micros(percentile(sorted, 0.99)),
                    micros(percentile(sorted, 0.999)),
                    micros(max)
            );
        }

        private static long percentile(long[] sorted, double percentile) {
            int index = (int) Math.ceil(percentile * sorted.length) - 1;
            return sorted[Math.max(0, Math.min(sorted.length - 1, index))];
        }

        private static double micros(double nanos) {
            return nanos / 1_000.0;
        }
    }

    private record GcSnapshot(long count, long timeMillis) {
        private static GcSnapshot capture() {
            long count = 0L;
            long time = 0L;
            for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
                count += Math.max(0L, bean.getCollectionCount());
                time += Math.max(0L, bean.getCollectionTime());
            }
            return new GcSnapshot(count, time);
        }
    }
}

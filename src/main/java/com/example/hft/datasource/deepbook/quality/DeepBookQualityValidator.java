package com.example.hft.datasource.deepbook.quality;

import com.example.hft.datasource.deepbook.DeepBookSourceDefinition;
import com.example.hft.datasource.deepbook.quality.KrakenBookChecksum.ChecksumLevel;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;


public final class DeepBookQualityValidator {
    private static final Duration DEFAULT_MAX_EVENT_AGE = Duration.ofSeconds(30);
    private static final Duration MAX_FUTURE_CLOCK_SKEW = Duration.ofSeconds(5);

    private final ObjectMapper mapper;
    private final Duration maxEventAge;

    public DeepBookQualityValidator() {
        this(DEFAULT_MAX_EVENT_AGE);
    }

    public DeepBookQualityValidator(Duration maxEventAge) {
        if (maxEventAge == null || maxEventAge.isNegative() || maxEventAge.isZero()) {
            throw new IllegalArgumentException("maxEventAge must be positive");
        }
        this.maxEventAge = maxEventAge;
        this.mapper = new ObjectMapper()
                .setNodeFactory(JsonNodeFactory.withExactBigDecimals(true))
                .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    }

    public DeepBookQualityReport validate(
            DeepBookSourceDefinition source,
            String snapshotPayload,
            List<String> webSocketPayloads,
            Instant validationTime
    ) {
        QualityAccumulator result = new QualityAccumulator();
        try {
            switch (source.exchange()) {
                case "BINANCE_US" -> validateBinance(
                        source, required(snapshotPayload, "Binance snapshot"), webSocketPayloads, validationTime, result);
                case "OKX" -> validateOkx(source, webSocketPayloads, validationTime, result);
                case "KRAKEN" -> validateKraken(source, webSocketPayloads, validationTime, result);
                default -> throw violation("schema", "unsupported exchange " + source.exchange());
            }
        } catch (QualityViolation e) {
            result.fail(e.check(), e.getMessage());
        } catch (Exception e) {
            result.fail("parse", e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        return result.toReport();
    }

    private void validateBinance(
            DeepBookSourceDefinition source,
            String snapshotPayload,
            List<String> updates,
            Instant validationTime,
            QualityAccumulator result
    ) throws Exception {
        requireMessageCoverage(updates);
        JsonNode snapshot = mapper.readTree(snapshotPayload);
        long snapshotUpdateId = positiveLong(snapshot, "lastUpdateId");
        DecimalBook book = new DecimalBook();
        book.loadArraySnapshot(snapshot.path("bids"), snapshot.path("asks"));
        book.requireUncrossed();
        result.pass("schema");
        result.pass("positive-levels");
        result.pass("side-ordering");
        result.pass("uncrossed-book");

        long previousUpdateId = snapshotUpdateId;
        for (int index = 0; index < updates.size(); index++) {
            JsonNode update = mapper.readTree(updates.get(index));
            requireTextEquals(update, "s", source.symbol());
            long firstUpdateId = positiveLong(update, "U");
            long finalUpdateId = positiveLong(update, "u");
            if (firstUpdateId > finalUpdateId) {
                throw violation("sequence", "Binance U is greater than u at message " + index);
            }
            long expected = previousUpdateId + 1;
            boolean contiguous = index == 0
                    ? firstUpdateId <= expected && finalUpdateId >= expected
                    : firstUpdateId == expected;
            if (!contiguous) {
                throw violation("sequence", "Binance gap at message " + index
                        + ": expected=" + expected + " U=" + firstUpdateId + " u=" + finalUpdateId);
            }
            requireFreshEpochMillis(positiveLong(update, "E"), validationTime, "Binance", index);
            book.applyArrayUpdates(update.path("b"), update.path("a"));
            book.requireUncrossed();
            previousUpdateId = finalUpdateId;
        }
        result.pass("sequence-continuity");
        result.pass("event-freshness");
        result.pass("post-update-uncrossed");
        result.sequenceDetails = "snapshot=" + snapshotUpdateId + ",last=" + previousUpdateId;
        result.checksumDetails = "not-provided";
        result.checkedMessages = updates.size();
    }

    private void validateOkx(
            DeepBookSourceDefinition source,
            List<String> messages,
            Instant validationTime,
            QualityAccumulator result
    ) throws Exception {
        requireMessageCoverage(messages);
        DecimalBook book = new DecimalBook();
        long previousSequence = Long.MIN_VALUE;

        for (int index = 0; index < messages.size(); index++) {
            JsonNode root = mapper.readTree(messages.get(index));
            requireTextEquals(root.path("arg"), "channel", source.channel());
            requireTextEquals(root.path("arg"), "instId", source.symbol());
            String action = requiredText(root, "action");
            JsonNode data = requiredFirst(root.path("data"), "OKX data");
            long sequence = nonNegativeLong(data, "seqId");
            long previous = data.path("prevSeqId").asLong(Long.MIN_VALUE);
            requireFreshEpochMillis(positiveLong(data, "ts"), validationTime, "OKX", index);

            if (index == 0) {
                if (!"snapshot".equals(action)) {
                    throw violation("sequence", "OKX first book message must be a snapshot");
                }
                book.loadArraySnapshot(data.path("bids"), data.path("asks"));
            } else {
                if (!"update".equals(action)) {
                    throw violation("sequence", "OKX message " + index + " must be an update");
                }
                if (previous != previousSequence || sequence < previous) {
                    throw violation("sequence", "OKX gap at message " + index
                            + ": expected prevSeqId=" + previousSequence
                            + " actual prevSeqId=" + previous + " seqId=" + sequence);
                }
                book.applyArrayUpdates(data.path("bids"), data.path("asks"));
            }
            book.truncate(source.depthLevels());
            book.requireUncrossed();
            previousSequence = sequence;
        }

        result.pass("schema");
        result.pass("positive-levels");
        result.pass("side-ordering");
        result.pass("sequence-continuity");
        result.pass("event-freshness");
        result.pass("post-update-uncrossed");
        result.sequenceDetails = "lastSeqId=" + previousSequence;
        result.checksumDetails = "not-applicable-okx-sequence-validation";
        result.checkedMessages = messages.size();
    }

    private void validateKraken(
            DeepBookSourceDefinition source,
            List<String> messages,
            Instant validationTime,
            QualityAccumulator result
    ) throws Exception {
        requireMessageCoverage(messages);
        DecimalBook book = new DecimalBook();
        Instant previousTimestamp = null;
        long lastExpectedChecksum = -1;

        for (int index = 0; index < messages.size(); index++) {
            JsonNode root = mapper.readTree(messages.get(index));
            requireTextEquals(root, "channel", source.channel());
            String type = requiredText(root, "type");
            JsonNode data = requiredFirst(root.path("data"), "Kraken data");
            requireTextEquals(data, "symbol", source.symbol());
            Instant timestamp = parseTimestamp(requiredText(data, "timestamp"), "Kraken", index);
            requireFreshInstant(timestamp, validationTime, "Kraken", index);
            if (previousTimestamp != null && timestamp.isBefore(previousTimestamp)) {
                throw violation("sequence", "Kraken timestamp moved backwards at message " + index);
            }

            if (index == 0) {
                if (!"snapshot".equals(type)) {
                    throw violation("sequence", "Kraken first book message must be a snapshot");
                }
                book.loadObjectSnapshot(data.path("bids"), data.path("asks"));
            } else {
                if (!"update".equals(type)) {
                    throw violation("sequence", "Kraken message " + index + " must be an update");
                }
                book.applyObjectUpdates(data.path("bids"), data.path("asks"));
            }
            book.truncate(source.depthLevels());
            book.requireUncrossed();

            long expectedChecksum = nonNegativeLong(data, "checksum");
            long actualChecksum = KrakenBookChecksum.calculate(book.topAsks(10), book.topBids(10));
            if (actualChecksum != expectedChecksum) {
                throw violation("checksum", "Kraken CRC32 mismatch at message " + index
                        + ": expected=" + expectedChecksum + " actual=" + actualChecksum);
            }
            lastExpectedChecksum = expectedChecksum;
            previousTimestamp = timestamp;
        }

        result.pass("schema");
        result.pass("positive-levels");
        result.pass("side-ordering");
        result.pass("message-order");
        result.pass("event-freshness");
        result.pass("post-update-uncrossed");
        result.pass("crc32-checksum");
        result.sequenceDetails = "lastTimestamp=" + previousTimestamp;
        result.checksumDetails = "crc32=" + lastExpectedChecksum;
        result.checkedMessages = messages.size();
    }

    private void requireMessageCoverage(List<String> messages) {
        if (messages == null || messages.size() < 2) {
            throw violation("coverage", "at least two book messages are required to validate continuity");
        }
    }

    private void requireFreshEpochMillis(long eventMillis, Instant now, String exchange, int messageIndex) {
        requireFreshInstant(Instant.ofEpochMilli(eventMillis), now, exchange, messageIndex);
    }

    private void requireFreshInstant(Instant eventTime, Instant now, String exchange, int messageIndex) {
        Duration age = Duration.between(eventTime, now);
        if (age.compareTo(maxEventAge) > 0) {
            throw violation("freshness", exchange + " message " + messageIndex + " is stale by " + age.toMillis() + "ms");
        }
        if (age.compareTo(MAX_FUTURE_CLOCK_SKEW.negated()) < 0) {
            throw violation("freshness", exchange + " message " + messageIndex + " is too far in the future");
        }
    }

    private static Instant parseTimestamp(String value, String exchange, int messageIndex) {
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            throw violation("freshness", exchange + " message " + messageIndex + " has invalid timestamp");
        }
    }

    private static JsonNode requiredFirst(JsonNode array, String label) {
        if (!array.isArray() || array.isEmpty()) {
            throw violation("schema", label + " must contain one book object");
        }
        return array.get(0);
    }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) {
            throw violation("schema", label + " is required");
        }
        return value;
    }

    private static String requiredText(JsonNode node, String field) {
        String value = node.path(field).asText("");
        if (value.isBlank()) {
            throw violation("schema", "missing text field " + field);
        }
        return value;
    }

    private static void requireTextEquals(JsonNode node, String field, String expected) {
        String actual = requiredText(node, field);
        if (!expected.equals(actual)) {
            throw violation("schema", "field " + field + " expected=" + expected + " actual=" + actual);
        }
    }

    private static long positiveLong(JsonNode node, String field) {
        long value = node.path(field).asLong(Long.MIN_VALUE);
        if (value <= 0) {
            throw violation("schema", "field " + field + " must be positive");
        }
        return value;
    }

    private static long nonNegativeLong(JsonNode node, String field) {
        long value = node.path(field).asLong(Long.MIN_VALUE);
        if (value < 0) {
            throw violation("schema", "field " + field + " must be non-negative");
        }
        return value;
    }

    private static QualityViolation violation(String check, String message) {
        return new QualityViolation(check, message);
    }

    private static final class DecimalBook {
        private final NavigableMap<BigDecimal, String> bids = new TreeMap<>(Comparator.reverseOrder());
        private final NavigableMap<BigDecimal, String> asks = new TreeMap<>();

        void loadArraySnapshot(JsonNode bidLevels, JsonNode askLevels) {
            bids.clear();
            asks.clear();
            loadArrayLevels(bidLevels, bids, true, true);
            loadArrayLevels(askLevels, asks, false, true);
        }

        void loadObjectSnapshot(JsonNode bidLevels, JsonNode askLevels) {
            bids.clear();
            asks.clear();
            loadObjectLevels(bidLevels, bids, true, true);
            loadObjectLevels(askLevels, asks, false, true);
        }

        void applyArrayUpdates(JsonNode bidLevels, JsonNode askLevels) {
            loadArrayLevels(bidLevels, bids, true, false);
            loadArrayLevels(askLevels, asks, false, false);
        }

        void applyObjectUpdates(JsonNode bidLevels, JsonNode askLevels) {
            loadObjectLevels(bidLevels, bids, true, false);
            loadObjectLevels(askLevels, asks, false, false);
        }

        void truncate(int depth) {
            while (bids.size() > depth) {
                bids.pollLastEntry();
            }
            while (asks.size() > depth) {
                asks.pollLastEntry();
            }
        }

        void requireUncrossed() {
            if (bids.isEmpty() || asks.isEmpty()) {
                throw violation("levels", "both bid and ask sides must be non-empty");
            }
            if (bids.firstKey().compareTo(asks.firstKey()) >= 0) {
                throw violation("uncrossed", "best bid " + bids.firstKey() + " is not below best ask " + asks.firstKey());
            }
        }

        List<ChecksumLevel> topBids(int levels) {
            return top(bids, levels);
        }

        List<ChecksumLevel> topAsks(int levels) {
            return top(asks, levels);
        }

        private static List<ChecksumLevel> top(NavigableMap<BigDecimal, String> side, int levels) {
            return side.entrySet().stream()
                    .limit(levels)
                    .map(entry -> new ChecksumLevel(entry.getKey().toPlainString(), entry.getValue()))
                    .toList();
        }

        private static void loadArrayLevels(
                JsonNode levels,
                NavigableMap<BigDecimal, String> side,
                boolean bidSide,
                boolean snapshot
        ) {
            requireArray(levels, bidSide ? "bids" : "asks");
            BigDecimal previousPrice = null;
            for (JsonNode level : levels) {
                if (!level.isArray() || level.size() < 2) {
                    throw violation("schema", "array book level must contain price and quantity");
                }
                String priceText = level.get(0).asText("");
                String quantityText = level.get(1).asText("");
                previousPrice = applyLevel(side, bidSide, snapshot, previousPrice, priceText, quantityText);
            }
        }

        private static void loadObjectLevels(
                JsonNode levels,
                NavigableMap<BigDecimal, String> side,
                boolean bidSide,
                boolean snapshot
        ) {
            requireArray(levels, bidSide ? "bids" : "asks");
            BigDecimal previousPrice = null;
            for (JsonNode level : levels) {
                String priceText = level.path("price").asText("");
                String quantityText = level.path("qty").asText("");
                previousPrice = applyLevel(side, bidSide, snapshot, previousPrice, priceText, quantityText);
            }
        }

        private static BigDecimal applyLevel(
                NavigableMap<BigDecimal, String> side,
                boolean bidSide,
                boolean snapshot,
                BigDecimal previousPrice,
                String priceText,
                String quantityText
        ) {
            if (priceText.isBlank() || quantityText.isBlank()) {
                throw violation("schema", "book level price and quantity are required");
            }
            BigDecimal price;
            BigDecimal quantity;
            try {
                price = new BigDecimal(priceText);
                quantity = new BigDecimal(quantityText);
            } catch (NumberFormatException e) {
                throw violation("schema", "book level contains a non-decimal value");
            }
            if (price.signum() <= 0 || quantity.signum() < 0 || snapshot && quantity.signum() == 0) {
                throw violation("levels", "book level has invalid price or quantity");
            }
            if (snapshot && previousPrice != null) {
                int comparison = previousPrice.compareTo(price);
                if (bidSide && comparison <= 0 || !bidSide && comparison >= 0) {
                    throw violation("ordering", (bidSide ? "bid" : "ask") + " snapshot is not strictly ordered");
                }
            }
            if (quantity.signum() == 0) {
                side.remove(price);
            } else {
                side.put(price, quantityText);
            }
            return price;
        }

        private static void requireArray(JsonNode levels, String label) {
            if (!levels.isArray()) {
                throw violation("schema", label + " must be an array");
            }
        }
    }

    private static final class QualityAccumulator {
        private final List<String> passedChecks = new ArrayList<>();
        private final List<String> failures = new ArrayList<>();
        private int checkedMessages;
        private String sequenceDetails = "";
        private String checksumDetails = "";

        void pass(String check) {
            passedChecks.add(check);
        }

        void fail(String check, String message) {
            failures.add(check + ":" + message);
        }

        DeepBookQualityReport toReport() {
            return new DeepBookQualityReport(
                    failures.isEmpty(),
                    checkedMessages,
                    passedChecks,
                    failures,
                    sequenceDetails,
                    checksumDetails
            );
        }
    }

    private static final class QualityViolation extends RuntimeException {
        private final String check;

        QualityViolation(String check, String message) {
            super(message);
            this.check = check;
        }

        String check() {
            return check;
        }
    }
}

package com.example.hft.datasource.deepbook.runtime;

import com.example.hft.datasource.deepbook.quality.KrakenBookChecksum.ChecksumLevel;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;


final class MutableDecimalOrderBook {
    private final NavigableMap<BigDecimal, String> bids = new TreeMap<>(Comparator.reverseOrder());
    private final NavigableMap<BigDecimal, String> asks = new TreeMap<>();

    void loadArraySnapshot(JsonNode bidLevels, JsonNode askLevels) {
        List<ParsedLevel> parsedBids = parseArrayLevels(bidLevels, true, true);
        List<ParsedLevel> parsedAsks = parseArrayLevels(askLevels, true, false);
        clear();
        applyLevels(parsedBids, bids);
        applyLevels(parsedAsks, asks);
    }

    void loadObjectSnapshot(JsonNode bidLevels, JsonNode askLevels) {
        List<ParsedLevel> parsedBids = parseObjectLevels(bidLevels, true, true);
        List<ParsedLevel> parsedAsks = parseObjectLevels(askLevels, true, false);
        clear();
        applyLevels(parsedBids, bids);
        applyLevels(parsedAsks, asks);
    }

    void applyArrayUpdates(JsonNode bidLevels, JsonNode askLevels) {
        List<ParsedLevel> parsedBids = parseArrayLevels(bidLevels, false, true);
        List<ParsedLevel> parsedAsks = parseArrayLevels(askLevels, false, false);
        applyLevels(parsedBids, bids);
        applyLevels(parsedAsks, asks);
    }

    void applyObjectUpdates(JsonNode bidLevels, JsonNode askLevels) {
        List<ParsedLevel> parsedBids = parseObjectLevels(bidLevels, false, true);
        List<ParsedLevel> parsedAsks = parseObjectLevels(askLevels, false, false);
        applyLevels(parsedBids, bids);
        applyLevels(parsedAsks, asks);
    }

    void clear() {
        bids.clear();
        asks.clear();
    }

    void truncate(int depth) {
        while (bids.size() > depth) {
            bids.pollLastEntry();
        }
        while (asks.size() > depth) {
            asks.pollLastEntry();
        }
    }

    boolean isCrossed() {
        return bids.isEmpty() || asks.isEmpty() || bids.firstKey().compareTo(asks.firstKey()) >= 0;
    }

    List<DecimalBookLevel> topBids(int levels) {
        return top(bids, levels);
    }

    List<DecimalBookLevel> topAsks(int levels) {
        return top(asks, levels);
    }

    List<ChecksumLevel> checksumBids() {
        return checksumLevels(bids);
    }

    List<ChecksumLevel> checksumAsks() {
        return checksumLevels(asks);
    }

    private static List<DecimalBookLevel> top(NavigableMap<BigDecimal, String> side, int levels) {
        return side.entrySet().stream()
                .limit(levels)
                .map(entry -> new DecimalBookLevel(entry.getKey(), new BigDecimal(entry.getValue())))
                .toList();
    }

    private static List<ChecksumLevel> checksumLevels(NavigableMap<BigDecimal, String> side) {
        return side.entrySet().stream()
                .limit(10)
                .map(entry -> new ChecksumLevel(entry.getKey().toPlainString(), entry.getValue()))
                .toList();
    }

    private static List<ParsedLevel> parseArrayLevels(
            JsonNode levels,
            boolean snapshot,
            boolean descending
    ) {
        requireArray(levels);
        List<ParsedLevel> parsed = new ArrayList<>(levels.size());
        for (JsonNode level : levels) {
            if (!level.isArray() || level.size() < 2) {
                throw new IllegalArgumentException("array level requires price and quantity");
            }
            parsed.add(parseLevel(
                    level.get(0).asText(""),
                    level.get(1).asText(""),
                    snapshot
            ));
        }
        if (snapshot) {
            requireStrictWireOrder(parsed, descending);
        }
        return parsed;
    }

    private static List<ParsedLevel> parseObjectLevels(
            JsonNode levels,
            boolean snapshot,
            boolean descending
    ) {
        requireArray(levels);
        List<ParsedLevel> parsed = new ArrayList<>(levels.size());
        for (JsonNode level : levels) {
            parsed.add(parseLevel(
                    level.path("price").asText(""),
                    level.path("qty").asText(""),
                    snapshot
            ));
        }
        if (snapshot) {
            requireStrictWireOrder(parsed, descending);
        }
        return parsed;
    }

    private static ParsedLevel parseLevel(
            String priceText,
            String quantityText,
            boolean snapshot
    ) {
        BigDecimal price = positiveDecimal(priceText, "price");
        BigDecimal quantity = nonNegativeDecimal(quantityText, "quantity");
        if (snapshot && quantity.signum() == 0) {
            throw new IllegalArgumentException("snapshot quantity must be positive");
        }
        return new ParsedLevel(price, quantity, quantityText);
    }

    private static void applyLevels(
            List<ParsedLevel> levels,
            NavigableMap<BigDecimal, String> target
    ) {
        for (ParsedLevel level : levels) {
            if (level.quantity().signum() == 0) {
                target.remove(level.price());
            } else {
                target.put(level.price(), level.quantityText());
            }
        }
    }

    private static void requireStrictWireOrder(
            List<ParsedLevel> levels,
            boolean descending
    ) {
        for (int index = 1; index < levels.size(); index++) {
            int comparison = levels.get(index - 1).price().compareTo(levels.get(index).price());
            if (descending && comparison <= 0 || !descending && comparison >= 0) {
                throw new IllegalArgumentException("snapshot levels are not strictly ordered");
            }
        }
    }

    private static BigDecimal positiveDecimal(String value, String label) {
        BigDecimal parsed = decimal(value, label);
        if (parsed.signum() <= 0) {
            throw new IllegalArgumentException(label + " must be positive");
        }
        return parsed;
    }

    private static BigDecimal nonNegativeDecimal(String value, String label) {
        BigDecimal parsed = decimal(value, label);
        if (parsed.signum() < 0) {
            throw new IllegalArgumentException(label + " must be non-negative");
        }
        return parsed;
    }

    private static BigDecimal decimal(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required");
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(label + " is not a decimal", e);
        }
    }

    private static void requireArray(JsonNode node) {
        if (!node.isArray()) {
            throw new IllegalArgumentException("book side must be an array");
        }
    }

    private record ParsedLevel(
            BigDecimal price,
            BigDecimal quantity,
            String quantityText
    ) {
    }
}

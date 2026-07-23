package com.example.hft.marketdata.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;




public final class LocalOrderBook {
    private static final int PRICE_SCALE = 100_000;
    private static final int SIZE_SCALE = 10_000;

    private final TreeMap<Long, Integer> bids = new TreeMap<>(Comparator.reverseOrder());
    private final TreeMap<Long, Integer> asks = new TreeMap<>();

    private long lastUpdateId;

    public void loadSnapshot(String payload) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(payload);
        bids.clear();
        asks.clear();
        lastUpdateId = root.get("lastUpdateId").asLong();
        loadSide(root.get("bids"), bids);
        loadSide(root.get("asks"), asks);
    }

    public boolean applyDepthUpdate(DepthUpdate update) {
        if (update.finalUpdateId() <= lastUpdateId) {
            return false;
        }
        applySide(update.bids(), bids);
        applySide(update.asks(), asks);
        lastUpdateId = update.finalUpdateId();
        return true;
    }

    public long lastUpdateId() {
        return lastUpdateId;
    }

    public DepthBookTop topLevels(int levels) {
        long[] bidPrices = new long[levels];
        int[] bidSizes = new int[levels];
        long[] askPrices = new long[levels];
        int[] askSizes = new int[levels];

        copySide(bids, bidPrices, bidSizes);
        copySide(asks, askPrices, askSizes);
        return new DepthBookTop(bidPrices, bidSizes, askPrices, askSizes);
    }

    private static void loadSide(JsonNode side, TreeMap<Long, Integer> target) {
        for (JsonNode level : side) {
            target.put(toTicks(level.get(0).asText()), Math.max(1, toSize(level.get(1).asText())));
        }
    }

    private static void applySide(List<OrderBookLevel> levels, TreeMap<Long, Integer> target) {
        for (OrderBookLevel level : levels) {
            if (level.size() <= 0) {
                target.remove(level.priceTicks());
            } else {
                target.put(level.priceTicks(), level.size());
            }
        }
    }

    private static void copySide(TreeMap<Long, Integer> source, long[] prices, int[] sizes) {
        int index = 0;
        for (var entry : source.entrySet()) {
            if (index >= prices.length) {
                return;
            }
            prices[index] = entry.getKey();
            sizes[index] = entry.getValue();
            index++;
        }
    }

    public static List<OrderBookLevel> parseLevels(JsonNode side) {
        List<OrderBookLevel> levels = new ArrayList<>(side.size());
        for (JsonNode level : side) {
            levels.add(new OrderBookLevel(toTicks(level.get(0).asText()), toSize(level.get(1).asText())));
        }
        return levels;
    }

    private static long toTicks(String value) {
        return new BigDecimal(value).multiply(BigDecimal.valueOf(PRICE_SCALE)).longValue();
    }

    private static int toSize(String value) {
        BigDecimal raw = new BigDecimal(value);
        if (raw.signum() == 0) {
            return 0;
        }
        BigDecimal scaled = raw.multiply(BigDecimal.valueOf(SIZE_SCALE));
        long size = scaled.longValue();
        if (size <= 0) {
            return 1;
        }
        return size > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) size;
    }
}

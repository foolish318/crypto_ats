package com.example.hft.datasource.deepbook.runtime;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.function.ToIntFunction;


final class BoundedBootstrapBuffer<T> {
    private final Queue<T> queue = new ArrayDeque<>();
    private final int maxEntries;
    private final long maxBytes;
    private final ToIntFunction<T> sizeEstimator;
    private long bytes;
    private long overflows;

    BoundedBootstrapBuffer(
            int maxEntries,
            long maxBytes,
            ToIntFunction<T> sizeEstimator
    ) {
        if (maxEntries <= 0 || maxBytes <= 0L) {
            throw new IllegalArgumentException("bootstrap buffer limits must be positive");
        }
        this.maxEntries = maxEntries;
        this.maxBytes = maxBytes;
        this.sizeEstimator = sizeEstimator;
    }

    boolean offer(T value) {
        int valueBytes = Math.max(0, sizeEstimator.applyAsInt(value));
        if (queue.size() >= maxEntries || bytes + valueBytes > maxBytes) {
            overflows++;
            return false;
        }
        queue.add(value);
        bytes += valueBytes;
        return true;
    }

    T poll() {
        T value = queue.poll();
        if (value != null) {
            bytes -= Math.max(0, sizeEstimator.applyAsInt(value));
        }
        return value;
    }

    boolean isEmpty() {
        return queue.isEmpty();
    }

    int size() {
        return queue.size();
    }

    long bytes() {
        return bytes;
    }

    long overflows() {
        return overflows;
    }

    void clear() {
        queue.clear();
        bytes = 0L;
    }
}
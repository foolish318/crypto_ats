package com.example.hft.marketdata.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.hft.datasource.deepbook.DeepBookSourceCatalog;
import com.example.hft.datasource.deepbook.runtime.BookUpdateStatus;
import com.example.hft.datasource.deepbook.runtime.LocalOrderBookBuilder;
import com.example.hft.datasource.deepbook.runtime.LocalOrderBookBuilderFactory;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class BookVersionTest {
    @Test
    void versionIncrementsOnlyForAppliedChangesAndSurvivesReset() {
        LocalOrderBookBuilder builder = LocalOrderBookBuilderFactory.create(
                DeepBookSourceCatalog.okx("BTC-USDT"), Duration.ofSeconds(10));
        long now = System.currentTimeMillis();
        String snapshot = message("snapshot", now, 10, -1,
                "[[\"100\",\"1\",\"0\",\"1\"],[\"99\",\"1\",\"0\",\"1\"]]",
                "[[\"101\",\"1\",\"0\",\"1\"]]");
        assertEquals(BookUpdateStatus.SNAPSHOT_LOADED,
                builder.onMessage(snapshot, now).status());
        assertEquals(1L, builder.snapshot(10).bookVersion());

        String update = message("update", now + 1, 11, 10,
                "[[\"100\",\"0\",\"0\",\"1\"]]", "[]");
        assertEquals(BookUpdateStatus.APPLIED,
                builder.onMessage(update, now + 1).status());
        assertEquals(2L, builder.snapshot(10).bookVersion());
        assertEquals("99", builder.snapshot(10).bestBid().price().toPlainString());

        String nextUpdate = message("update", now + 2, 12, 11,
                "[]", "[[\"101\",\"2\",\"0\",\"1\"]]");
        assertEquals(BookUpdateStatus.APPLIED,
                builder.onMessage(nextUpdate, now + 2).status());
        assertEquals(3L, builder.snapshot(10).bookVersion());

        builder.reset();
        assertEquals(3L, builder.snapshot(10).bookVersion());
        assertTrue(builder.snapshot(10).bids().isEmpty());
    }

    private static String message(
            String action,
            long timestamp,
            long sequence,
            long previous,
            String bids,
            String asks
    ) {
        return "{\"arg\":{\"channel\":\"books\",\"instId\":\"BTC-USDT\"},"
                + "\"action\":\"" + action + "\",\"data\":[{\"bids\":" + bids
                + ",\"asks\":" + asks + ",\"ts\":\"" + timestamp
                + "\",\"seqId\":" + sequence + ",\"prevSeqId\":" + previous + "}]}";
    }
}
package com.example.hft.datasource.deepbook.runtime;

import com.example.hft.datasource.book.BookQuality;


public record BookUpdateResult(
        BookUpdateStatus status,
        BookQuality quality,
        long sequence,
        long eventTimeMillis,
        long parseNanos,
        long bookNanos,
        String detail
) {
    public BookUpdateResult {
        detail = detail == null ? "" : detail;
    }

    public boolean accepted() {
        return status == BookUpdateStatus.SNAPSHOT_LOADED || status == BookUpdateStatus.APPLIED;
    }

    public boolean requiresRecovery() {
        return status == BookUpdateStatus.GAP
                || status == BookUpdateStatus.CHECKSUM_FAILED
                || status == BookUpdateStatus.CROSSED
                || status == BookUpdateStatus.PARSE_FAILED;
    }
}

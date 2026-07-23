package com.example.hft.datasource.deepbook.runtime;

import com.example.hft.datasource.book.BookQuality;


public enum BookState {
    EMPTY,
    BOOTSTRAPPING,
    LIVE,
    STALE,
    GAP_DETECTED,
    CHECKSUM_FAILED,
    CROSSED,
    DEGRADED;

    public static BookState from(BookQuality quality) {
        return switch (quality) {
            case EMPTY -> EMPTY;
            case BOOTSTRAPPING -> BOOTSTRAPPING;
            case LIVE -> LIVE;
            case STALE -> STALE;
            case GAP_DETECTED -> GAP_DETECTED;
            case CHECKSUM_FAILED -> CHECKSUM_FAILED;
            case CROSSED -> CROSSED;
            case DEGRADED -> DEGRADED;
        };
    }
}

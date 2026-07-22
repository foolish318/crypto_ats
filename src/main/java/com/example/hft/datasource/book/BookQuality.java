package com.example.hft.datasource.book;


public enum BookQuality {
    EMPTY,
    BOOTSTRAPPING,
    LIVE,
    STALE,
    GAP_DETECTED,
    CHECKSUM_FAILED,
    CROSSED,
    DEGRADED
}

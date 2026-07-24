package com.example.hft.datasource.deepbook.runtime;

public enum BookAvailabilityState {
    LIVE,
    STALE,
    GAP,
    CHECKSUM_FAILED,
    RECOVERING,
    DISCONNECTED,
    INVALID,
    STOPPED
}
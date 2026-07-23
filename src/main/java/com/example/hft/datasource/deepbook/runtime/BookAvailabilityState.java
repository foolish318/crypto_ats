package com.example.hft.datasource.deepbook.runtime;


public enum BookAvailabilityState {
    LIVE,
    STALE,
    RECOVERING,
    DISCONNECTED,
    INVALID,
    STOPPED
}

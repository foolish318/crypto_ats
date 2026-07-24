package com.example.hft.marketdata.model;

public enum BookHealth {
    BOOTSTRAPPING,
    LIVE,
    STALE,
    GAP,
    CHECKSUM_FAILED,
    RECOVERING,
    DISCONNECTED,
    INVALID
}
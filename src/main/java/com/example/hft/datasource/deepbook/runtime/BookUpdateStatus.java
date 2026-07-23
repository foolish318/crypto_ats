package com.example.hft.datasource.deepbook.runtime;


public enum BookUpdateStatus {
    SNAPSHOT_LOADED,
    APPLIED,
    STALE,
    IGNORED,
    GAP,
    CHECKSUM_FAILED,
    CROSSED,
    PARSE_FAILED
}

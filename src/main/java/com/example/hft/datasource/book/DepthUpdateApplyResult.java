package com.example.hft.datasource.book;


public enum DepthUpdateApplyResult {
    APPLIED,
    STALE,
    GAP,
    CROSSED,
    UNKNOWN_SYMBOL
}
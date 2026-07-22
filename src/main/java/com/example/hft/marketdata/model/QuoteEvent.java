package com.example.hft.marketdata.model;


public sealed interface QuoteEvent permits QuoteMessage, StopMessage {
    boolean isStop();
}

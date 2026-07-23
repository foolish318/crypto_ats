package com.example.hft.datasource.deepbook.runtime;


public enum ProtocolMessageType {
    BOOK_DATA,
    SUBSCRIPTION_ACK,
    HEARTBEAT,
    PONG,
    ERROR,
    OTHER_CONTROL
}
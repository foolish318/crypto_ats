package com.example.hft.datasource.deepbook.runtime;


public record ProtocolMessageDecision(
        ProtocolMessageType type,
        String detail
) {
    public ProtocolMessageDecision {
        detail = detail == null ? "" : detail;
    }

    public boolean bookData() {
        return type == ProtocolMessageType.BOOK_DATA;
    }

    public boolean fatal() {
        return type == ProtocolMessageType.ERROR;
    }
}
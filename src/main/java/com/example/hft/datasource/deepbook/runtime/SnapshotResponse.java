package com.example.hft.datasource.deepbook.runtime;


public record SnapshotResponse(int statusCode, String body) {
    public SnapshotResponse {
        body = body == null ? "" : body;
    }

    public boolean successful() {
        return statusCode / 100 == 2;
    }
}

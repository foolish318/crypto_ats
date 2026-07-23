package com.example.hft.datasource.deepbook.runtime;

import com.example.hft.datasource.deepbook.DeepBookSourceDefinition;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.concurrent.CompletionStage;


public final class JdkVenueTransport implements VenueTransport {
    private final HttpClient client;

    public JdkVenueTransport(HttpClient client) {
        this.client = client;
    }

    @Override
    public CompletionStage<WebSocket> connect(
            DeepBookSourceDefinition source,
            Duration timeout,
            WebSocket.Listener listener
    ) {
        return client.newWebSocketBuilder()
                .header("User-Agent", "hft-java-learning/0.1")
                .connectTimeout(timeout)
                .buildAsync(source.webSocketUri(), listener);
    }
}

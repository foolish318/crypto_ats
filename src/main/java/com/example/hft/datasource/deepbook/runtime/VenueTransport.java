package com.example.hft.datasource.deepbook.runtime;

import com.example.hft.datasource.deepbook.DeepBookSourceDefinition;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.concurrent.CompletionStage;


public interface VenueTransport {
    CompletionStage<WebSocket> connect(
            DeepBookSourceDefinition source,
            Duration timeout,
            WebSocket.Listener listener
    );
}

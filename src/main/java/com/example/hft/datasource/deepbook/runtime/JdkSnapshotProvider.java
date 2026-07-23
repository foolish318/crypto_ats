package com.example.hft.datasource.deepbook.runtime;

import com.example.hft.datasource.deepbook.DeepBookSourceDefinition;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletionStage;


public final class JdkSnapshotProvider implements SnapshotProvider {
    private final HttpClient client;

    public JdkSnapshotProvider(HttpClient client) {
        this.client = client;
    }

    @Override
    public CompletionStage<SnapshotResponse> load(
            DeepBookSourceDefinition source,
            Duration timeout
    ) {
        HttpRequest request = HttpRequest.newBuilder(source.snapshotUri())
                .timeout(timeout)
                .GET()
                .build();
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> new SnapshotResponse(
                        response.statusCode(),
                        response.body()
                ));
    }
}

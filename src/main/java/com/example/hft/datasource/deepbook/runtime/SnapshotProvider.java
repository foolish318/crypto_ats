package com.example.hft.datasource.deepbook.runtime;

import com.example.hft.datasource.deepbook.DeepBookSourceDefinition;
import java.time.Duration;
import java.util.concurrent.CompletionStage;


public interface SnapshotProvider {
    CompletionStage<SnapshotResponse> load(
            DeepBookSourceDefinition source,
            Duration timeout
    );
}

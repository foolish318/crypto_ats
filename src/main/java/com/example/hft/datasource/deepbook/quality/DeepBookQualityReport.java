package com.example.hft.datasource.deepbook.quality;

import java.util.List;


public record DeepBookQualityReport(
        boolean accepted,
        int checkedMessages,
        List<String> passedChecks,
        List<String> failures,
        String sequenceDetails,
        String checksumDetails
) {
    public DeepBookQualityReport {
        passedChecks = List.copyOf(passedChecks);
        failures = List.copyOf(failures);
        sequenceDetails = sequenceDetails == null ? "" : sequenceDetails;
        checksumDetails = checksumDetails == null ? "" : checksumDetails;
    }

    public int passedCount() {
        return passedChecks.size();
    }

    public int failedCount() {
        return failures.size();
    }

    public String passedSummary() {
        return String.join(",", passedChecks);
    }

    public String failureSummary() {
        return failures.isEmpty() ? "none" : String.join(";", failures);
    }
}

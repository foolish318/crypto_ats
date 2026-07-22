package com.example.hft.benchmark;


public final class ModuleTiming {
    private long validationNanos;
    private long decisionNanos;
    private long analysisNanos;

    public void addValidationNanos(long nanos) {
        validationNanos += nanos;
    }

    public void addDecisionNanos(long nanos) {
        decisionNanos += nanos;
    }

    public void addAnalysisNanos(long nanos) {
        analysisNanos += nanos;
    }

    public void merge(ModuleTiming other) {
        validationNanos += other.validationNanos;
        decisionNanos += other.decisionNanos;
        analysisNanos += other.analysisNanos;
    }

    public long validationNanos() {
        return validationNanos;
    }

    public long decisionNanos() {
        return decisionNanos;
    }

    public long analysisNanos() {
        return analysisNanos;
    }
}

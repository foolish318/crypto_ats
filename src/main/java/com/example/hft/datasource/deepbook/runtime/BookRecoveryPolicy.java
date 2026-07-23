package com.example.hft.datasource.deepbook.runtime;


public interface BookRecoveryPolicy extends AutoCloseable {
    boolean requestRecovery(String reason);

    void connectEstablished();

    void connectFailed(String reason);

    void recoveredLive();

    RecoverySnapshot snapshot();

    boolean stopped();

    @Override
    void close();
}

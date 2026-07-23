package com.example.hft.datasource.deepbook.runtime;


public interface VenueProtocolStateMachine extends AutoCloseable {
    void connected(long generation);

    ProtocolMessageDecision onText(String payload);

    void onProtocolPing();

    void onProtocolPong();

    void disconnected();

    boolean bookDataAllowed();

    ProtocolSnapshot snapshot();

    void check();

    @Override
    void close();
}

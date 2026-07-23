package com.example.hft.datasource.deepbook.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.hft.datasource.deepbook.DeepBookSourceCatalog;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;


class VenueProtocolMessageClassifierTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void classifiesOkxAcknowledgementBookPongAndError() {
        VenueProtocolMessageClassifier classifier = new VenueProtocolMessageClassifier(
                DeepBookSourceCatalog.okx("BTC-USDT"), mapper);

        assertEquals(ProtocolMessageType.SUBSCRIPTION_ACK,
                classifier.classify("{\"event\":\"subscribe\",\"code\":\"0\"}").type());
        assertEquals(ProtocolMessageType.BOOK_DATA,
                classifier.classify("{\"arg\":{\"channel\":\"books\","
                        + "\"instId\":\"BTC-USDT\"},\"action\":\"update\"}").type());
        assertEquals(ProtocolMessageType.PONG, classifier.classify("pong").type());
        assertTrue(classifier.classify(
                "{\"event\":\"error\",\"code\":\"60012\",\"msg\":\"bad arg\"}")
                .fatal());
    }

    @Test
    void classifiesKrakenControlAndDataMessages() {
        VenueProtocolMessageClassifier classifier = new VenueProtocolMessageClassifier(
                DeepBookSourceCatalog.kraken("BTC/USD"), mapper);

        assertEquals(ProtocolMessageType.SUBSCRIPTION_ACK,
                classifier.classify("{\"method\":\"subscribe\",\"success\":true}").type());
        assertEquals(ProtocolMessageType.HEARTBEAT,
                classifier.classify("{\"channel\":\"heartbeat\"}").type());
        assertEquals(ProtocolMessageType.BOOK_DATA,
                classifier.classify(
                        "{\"channel\":\"book\",\"type\":\"snapshot\"}").type());
    }

    @Test
    void malformedPayloadFailsClosed() {
        VenueProtocolMessageClassifier classifier = new VenueProtocolMessageClassifier(
                DeepBookSourceCatalog.binanceUs("BTCUSDT"), mapper);

        ProtocolMessageDecision decision = classifier.classify("not-json");

        assertEquals(ProtocolMessageType.ERROR, decision.type());
        assertTrue(decision.fatal());
    }
}
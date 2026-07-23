package com.example.hft.datasource.deepbook.runtime;

import com.example.hft.datasource.deepbook.DeepBookSourceDefinition;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;


public final class VenueProtocolMessageClassifier {
    private final DeepBookSourceDefinition source;
    private final ObjectMapper mapper;

    public VenueProtocolMessageClassifier(
            DeepBookSourceDefinition source,
            ObjectMapper mapper
    ) {
        this.source = source;
        this.mapper = mapper;
    }

    public ProtocolMessageDecision classify(String payload) {
        if ("OKX".equals(source.exchange()) && "pong".equals(payload)) {
            return new ProtocolMessageDecision(ProtocolMessageType.PONG, "OKX pong");
        }
        try {
            JsonNode root = mapper.readTree(payload);
            return switch (source.exchange()) {
                case "BINANCE_US" -> classifyBinance(root);
                case "OKX" -> classifyOkx(root);
                case "KRAKEN" -> classifyKraken(root);
                default -> new ProtocolMessageDecision(
                        ProtocolMessageType.ERROR,
                        "unsupported protocol venue " + source.exchange()
                );
            };
        } catch (Exception error) {
            return new ProtocolMessageDecision(
                    ProtocolMessageType.ERROR,
                    "malformed " + source.exchange() + " payload: " + error.getMessage()
            );
        }
    }

    private ProtocolMessageDecision classifyBinance(JsonNode root) {
        JsonNode event = root.has("data") ? root.path("data") : root;
        if ("depthUpdate".equals(event.path("e").asText())) {
            return new ProtocolMessageDecision(ProtocolMessageType.BOOK_DATA, "");
        }
        if (root.has("code") || root.has("error")) {
            return error(root, "Binance WebSocket error");
        }
        return new ProtocolMessageDecision(
                ProtocolMessageType.OTHER_CONTROL,
                "Binance control message"
        );
    }

    private ProtocolMessageDecision classifyOkx(JsonNode root) {
        String event = root.path("event").asText("");
        String code = root.path("code").asText("0");
        if ("error".equals(event) || (!code.isBlank() && !"0".equals(code))) {
            return error(root, "OKX WebSocket error");
        }
        if ("subscribe".equals(event)) {
            return new ProtocolMessageDecision(
                    ProtocolMessageType.SUBSCRIPTION_ACK,
                    "OKX subscription acknowledged"
            );
        }
        String action = root.path("action").asText("");
        if (source.channel().equals(root.path("arg").path("channel").asText())
                && source.symbol().equals(root.path("arg").path("instId").asText())
                && ("snapshot".equals(action) || "update".equals(action))) {
            return new ProtocolMessageDecision(ProtocolMessageType.BOOK_DATA, "");
        }
        return new ProtocolMessageDecision(
                ProtocolMessageType.OTHER_CONTROL,
                "OKX control message"
        );
    }

    private ProtocolMessageDecision classifyKraken(JsonNode root) {
        if ("book".equals(root.path("channel").asText())
                && ("snapshot".equals(root.path("type").asText())
                || "update".equals(root.path("type").asText()))) {
            return new ProtocolMessageDecision(ProtocolMessageType.BOOK_DATA, "");
        }
        if ("heartbeat".equals(root.path("channel").asText())) {
            return new ProtocolMessageDecision(
                    ProtocolMessageType.HEARTBEAT,
                    "Kraken heartbeat"
            );
        }
        String method = root.path("method").asText("");
        if ("pong".equals(method)) {
            return new ProtocolMessageDecision(ProtocolMessageType.PONG, "Kraken pong");
        }
        if ("subscribe".equals(method)) {
            if (root.path("success").asBoolean(false)) {
                return new ProtocolMessageDecision(
                        ProtocolMessageType.SUBSCRIPTION_ACK,
                        "Kraken subscription acknowledged"
                );
            }
            return error(root, "Kraken subscription failed");
        }
        if (root.has("error") || root.path("success").isBoolean()
                && !root.path("success").asBoolean()) {
            return error(root, "Kraken WebSocket error");
        }
        return new ProtocolMessageDecision(
                ProtocolMessageType.OTHER_CONTROL,
                "Kraken control message"
        );
    }

    private static ProtocolMessageDecision error(JsonNode root, String prefix) {
        String code = root.path("code").asText("");
        String message = root.path("msg").asText(
                root.path("error").asText("unknown error"));
        String detail = prefix
                + (code.isBlank() ? "" : " code=" + code)
                + " message=" + message;
        return new ProtocolMessageDecision(ProtocolMessageType.ERROR, detail);
    }
}
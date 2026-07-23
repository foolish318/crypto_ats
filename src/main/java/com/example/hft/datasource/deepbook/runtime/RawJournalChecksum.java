package com.example.hft.datasource.deepbook.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.MessageDigest;
import java.util.HexFormat;


final class RawJournalChecksum {
    private RawJournalChecksum() {
    }

    static String record(ObjectMapper mapper, RawEnvelope envelope) {
        try {
            return bytes(mapper.writeValueAsBytes(envelope));
        } catch (Exception error) {
            throw new IllegalArgumentException("cannot checksum raw envelope", error);
        }
    }

    static String text(String value) {
        return bytes(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static String bytes(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (Exception error) {
            throw new IllegalStateException("SHA-256 unavailable", error);
        }
    }
}

package com.example.hft.datasource.deepbook.quality;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.zip.CRC32;


public final class KrakenBookChecksum {
    private static final int CHECKSUM_LEVELS = 10;
    private static final Comparator<ChecksumLevel> ASCENDING_PRICE =
            Comparator.comparing(level -> new BigDecimal(level.price()));
    private static final Comparator<ChecksumLevel> DESCENDING_PRICE = ASCENDING_PRICE.reversed();

    private KrakenBookChecksum() {
    }

    public static long calculate(List<ChecksumLevel> asks, List<ChecksumLevel> bids) {
        StringBuilder input = new StringBuilder();
        asks.stream()
                .sorted(ASCENDING_PRICE)
                .limit(CHECKSUM_LEVELS)
                .forEach(level -> append(input, level));
        bids.stream()
                .sorted(DESCENDING_PRICE)
                .limit(CHECKSUM_LEVELS)
                .forEach(level -> append(input, level));

        CRC32 crc32 = new CRC32();
        crc32.update(input.toString().getBytes(StandardCharsets.UTF_8));
        return crc32.getValue();
    }

    private static void append(StringBuilder input, ChecksumLevel level) {
        input.append(normalize(level.price()));
        input.append(normalize(level.quantity()));
    }

    private static String normalize(String value) {
        String digits = new BigDecimal(value).toPlainString().replace(".", "");
        int firstNonZero = 0;
        while (firstNonZero < digits.length() - 1 && digits.charAt(firstNonZero) == '0') {
            firstNonZero++;
        }
        return digits.substring(firstNonZero);
    }

    public record ChecksumLevel(String price, String quantity) {
        public ChecksumLevel {
            if (price == null || price.isBlank() || quantity == null || quantity.isBlank()) {
                throw new IllegalArgumentException("checksum level requires price and quantity");
            }
        }
    }
}

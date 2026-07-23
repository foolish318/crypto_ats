package com.example.hft.datasource.deepbook.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.hft.datasource.deepbook.DeepBookSourceCatalog;
import com.example.hft.datasource.deepbook.DeepBookSourceDefinition;
import com.example.hft.datasource.instrument.Instrument;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;


class InstrumentValidationTest {
    @Test
    void rejectsPriceOutsideVenueTickSize() {
        DeepBookSourceDefinition source = DeepBookSourceCatalog.okx("BTC-USDT");
        Instrument instrument = instrument();
        LocalOrderBookBuilder builder = LocalOrderBookBuilderFactory.create(source, instrument);
        String invalid = "{\"arg\":{\"channel\":\"books\","
                + "\"instId\":\"BTC-USDT\"},\"action\":\"snapshot\",\"data\":[{"
                + "\"bids\":[[\"100.05\",\"1.00\",\"0\",\"1\"]],"
                + "\"asks\":[[\"101.0\",\"1.00\",\"0\",\"1\"]],"
                + "\"ts\":\"1000\",\"seqId\":10,\"prevSeqId\":-1}]}";

        BookUpdateResult result = builder.onMessage(invalid, System.currentTimeMillis());

        assertEquals(BookUpdateStatus.PARSE_FAILED, result.status());
        assertEquals(com.example.hft.datasource.book.BookQuality.DEGRADED, builder.quality());
    }

    @Test
    void acceptsAggregatedBookQuantityThatIsNotAnOrderLotMultiple() {
        DeepBookSourceDefinition source = DeepBookSourceCatalog.okx("BTC-USDT");
        LocalOrderBookBuilder builder = LocalOrderBookBuilderFactory.create(source, instrument());
        long now = System.currentTimeMillis();
        String snapshot = "{\"arg\":{\"channel\":\"books\","
                + "\"instId\":\"BTC-USDT\"},\"action\":\"snapshot\",\"data\":[{"
                + "\"bids\":[[\"100.0\",\"1.005\",\"0\",\"1\"]],"
                + "\"asks\":[[\"101.0\",\"2.005\",\"0\",\"1\"]],"
                + "\"ts\":\"" + now + "\",\"seqId\":10,\"prevSeqId\":-1}]}";

        BookUpdateResult result = builder.onMessage(snapshot, now);

        assertEquals(BookUpdateStatus.SNAPSHOT_LOADED, result.status());
        assertEquals(com.example.hft.datasource.book.BookQuality.LIVE, builder.quality());
    }

    private static Instrument instrument() {
        return new Instrument(
                "OKX", "BTC-USDT", "BTC-USDT", "BTC", "USDT",
                new BigDecimal("0.1"), new BigDecimal("0.01"));
    }
}
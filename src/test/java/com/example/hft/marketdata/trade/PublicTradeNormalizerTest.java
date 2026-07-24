package com.example.hft.marketdata.trade;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.hft.datasource.engine.MarketDataCache;
import com.example.hft.datasource.engine.MarketDataEngine;
import com.example.hft.datasource.engine.MarketDataEventBus;
import com.example.hft.datasource.instrument.Instrument;
import com.example.hft.marketdata.api.DefaultStrategyMarketDataPort;
import com.example.hft.marketdata.model.AggressorSide;
import com.example.hft.marketdata.model.InstrumentId;
import com.example.hft.marketdata.model.PublicTrade;
import com.example.hft.marketdata.model.Venue;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class PublicTradeNormalizerTest {
    @Test
    void normalizesBinanceTradeFixture() throws Exception {
        PublicTradeSourceDefinition source = PublicTradeSourceCatalog.binanceUs("BTCUSDT");
        AtomicLong sequence = new AtomicLong();
        List<PublicTrade> trades = new BinancePublicTradeNormalizer(
                source, instrument(Venue.BINANCE_US, "BTCUSDT"), new ObjectMapper())
                .normalize("{\"e\":\"trade\",\"s\":\"BTCUSDT\",\"t\":42,"
                                + "\"p\":\"100.1\",\"q\":\"0.25\",\"T\":1000,\"m\":true}",
                        3L, 1_010L, 99L, sequence::incrementAndGet);

        assertEquals(1, trades.size());
        assertEquals(AggressorSide.SELL, trades.get(0).aggressorSide());
        assertEquals(new InstrumentId("BTC-USDT"), trades.get(0).header().instrumentId());
        assertEquals(3L, trades.get(0).header().streamEpoch());
    }

    @Test
    void normalizesOkxTradeFixture() throws Exception {
        PublicTradeSourceDefinition source = PublicTradeSourceCatalog.okx("BTC-USDT");
        List<PublicTrade> trades = new OkxPublicTradeNormalizer(
                source, instrument(Venue.OKX, "BTC-USDT"), new ObjectMapper())
                .normalize("{\"arg\":{\"channel\":\"trades\",\"instId\":\"BTC-USDT\"},"
                                + "\"data\":[{\"instId\":\"BTC-USDT\",\"tradeId\":\"43\","
                                + "\"px\":\"100.2\",\"sz\":\"0.5\",\"side\":\"buy\",\"ts\":\"1001\"}]}",
                        4L, 1_011L, 100L, new AtomicLong()::incrementAndGet);

        assertEquals(AggressorSide.BUY, trades.get(0).aggressorSide());
        assertEquals(new BigDecimal("100.2"), trades.get(0).price());
    }

    @Test
    void normalizesKrakenTradeFixture() throws Exception {
        PublicTradeSourceDefinition source = PublicTradeSourceCatalog.kraken("BTC/USDT");
        List<PublicTrade> trades = new KrakenPublicTradeNormalizer(
                source, instrument(Venue.KRAKEN, "BTC/USDT"), new ObjectMapper())
                .normalize("{\"channel\":\"trade\",\"type\":\"update\",\"data\":[{"
                                + "\"symbol\":\"BTC/USDT\",\"side\":\"sell\",\"price\":\"100.3\","
                                + "\"qty\":\"0.75\",\"trade_id\":\"44\","
                                + "\"timestamp\":\"1970-01-01T00:00:01.002Z\"}]}",
                        5L, 1_012L, 101L, new AtomicLong()::incrementAndGet);

        assertEquals(AggressorSide.SELL, trades.get(0).aggressorSide());
        assertEquals(1_002L, trades.get(0).header().exchangeEpochMillis());
    }

    @Test
    void pipelineDropsDuplicateAndMeasuresOutOfOrderWithoutInventingOrdering() {
        MarketDataEventBus bus = new MarketDataEventBus();
        MarketDataCache cache = new MarketDataCache();
        MarketDataEngine engine = new MarketDataEngine(cache, bus);
        DefaultStrategyMarketDataPort port = new DefaultStrategyMarketDataPort();
        bus.subscribe(port);
        PublicTradeSourceDefinition source = PublicTradeSourceCatalog.binanceUs("BTCUSDT");
        PublicTradePipeline pipeline = new PublicTradePipeline(
                new BinancePublicTradeNormalizer(
                        source, instrument(Venue.BINANCE_US, "BTCUSDT"), new ObjectMapper()),
                engine
        );
        String first = "{\"e\":\"trade\",\"s\":\"BTCUSDT\",\"t\":42,"
                + "\"p\":\"100.1\",\"q\":\"0.25\",\"T\":1000,\"m\":false}";
        String older = "{\"e\":\"trade\",\"s\":\"BTCUSDT\",\"t\":43,"
                + "\"p\":\"100.0\",\"q\":\"0.20\",\"T\":999,\"m\":true}";

        pipeline.onMessage(first, 1L, 1_010L, 10L);
        pipeline.onMessage(first, 1L, 1_011L, 11L);
        pipeline.onMessage(older, 1L, 1_012L, 12L);

        assertEquals(2L, pipeline.published());
        assertEquals(1L, pipeline.duplicates());
        assertEquals(1L, pipeline.outOfOrder());
        assertEquals("43", port.latestTrade(Venue.BINANCE_US, new InstrumentId("BTC-USDT"))
                .orElseThrow().tradeId());
    }

    private static Instrument instrument(Venue venue, String symbol) {
        return new Instrument(
                venue.name(), symbol, "BTC-USDT", "BTC", "USDT",
                new BigDecimal("0.1"), new BigDecimal("0.0001")
        );
    }
}
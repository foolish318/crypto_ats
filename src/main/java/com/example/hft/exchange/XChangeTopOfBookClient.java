package com.example.hft.exchange;

import com.example.hft.marketdata.model.TopOfBookSnapshot;
import java.math.BigDecimal;
import java.time.Instant;
import org.knowm.xchange.binance.BinanceUsExchange;
import org.knowm.xchange.coinbasepro.CoinbaseProExchange;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.kraken.KrakenExchange;




public final class XChangeTopOfBookClient {
    public TopOfBookSnapshot fetch(String exchange, String symbol) throws Exception {
        Class<? extends Exchange> exchangeClass = exchangeClass(exchange);
        CurrencyPair pair = currencyPair(exchange, symbol);
        long start = System.nanoTime();
        Exchange xchange = ExchangeFactory.INSTANCE.createExchange(exchangeClass);
        OrderBook orderBook = xchange.getMarketDataService().getOrderBook(pair);
        LimitOrder bid = first(orderBook.getBids());
        LimitOrder ask = first(orderBook.getAsks());
        if (bid == null || ask == null) {
            throw new IllegalStateException("XChange returned empty book for " + exchange + " " + symbol);
        }
        return new TopOfBookSnapshot("XCHANGE", exchange, symbol,
                bid.getLimitPrice(),
                valueOrZero(bid.getOriginalAmount()),
                ask.getLimitPrice(),
                valueOrZero(ask.getOriginalAmount()),
                Instant.now(),
                System.nanoTime() - start);
    }

    private static Class<? extends Exchange> exchangeClass(String exchange) {
        return switch (exchange) {
            case "BINANCE_US" -> BinanceUsExchange.class;
            case "COINBASE" -> CoinbaseProExchange.class;
            case "KRAKEN" -> KrakenExchange.class;
            default -> throw new IllegalArgumentException("unsupported exchange: " + exchange);
        };
    }

    private static CurrencyPair currencyPair(String exchange, String symbol) {
        return switch (exchange + ":" + symbol) {
            case "BINANCE_US:BTCUSDT" -> CurrencyPair.BTC_USDT;
            case "BINANCE_US:ETHUSDT" -> CurrencyPair.ETH_USDT;
            case "COINBASE:BTC-USD" -> CurrencyPair.BTC_USD;
            case "COINBASE:ETH-USD" -> CurrencyPair.ETH_USD;
            case "KRAKEN:BTC/USD" -> CurrencyPair.BTC_USD;
            case "KRAKEN:ETH/USD" -> CurrencyPair.ETH_USD;
            default -> throw new IllegalArgumentException("unsupported pair: " + exchange + " " + symbol);
        };
    }

    private static LimitOrder first(java.util.List<LimitOrder> orders) {
        return orders == null || orders.isEmpty() ? null : orders.get(0);
    }

    private static BigDecimal valueOrZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}

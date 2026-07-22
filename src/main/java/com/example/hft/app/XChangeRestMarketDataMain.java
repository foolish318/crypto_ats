package com.example.hft.app;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.knowm.xchange.binance.BinanceUsExchange;
import org.knowm.xchange.coinbasepro.CoinbaseProExchange;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.kraken.KrakenExchange;
import org.knowm.xchange.service.marketdata.MarketDataService;




public final class XChangeRestMarketDataMain {
    private XChangeRestMarketDataMain() {
    }

    public static void main(String[] args) {
        List<VenueRequest> requests = List.of(
                new VenueRequest("BINANCE_US", BinanceUsExchange.class, CurrencyPair.BTC_USDT),
                new VenueRequest("BINANCE_US", BinanceUsExchange.class, CurrencyPair.ETH_USDT),
                new VenueRequest("COINBASE", CoinbaseProExchange.class, CurrencyPair.BTC_USD),
                new VenueRequest("COINBASE", CoinbaseProExchange.class, CurrencyPair.ETH_USD),
                new VenueRequest("KRAKEN", KrakenExchange.class, CurrencyPair.BTC_USD),
                new VenueRequest("KRAKEN", KrakenExchange.class, CurrencyPair.ETH_USD)
        );

        System.out.println("xchange-rest sampledAt=" + Instant.now());
        for (VenueRequest request : requests) {
            sample(request);
        }
    }

    private static void sample(VenueRequest request) {
        long startNanos = System.nanoTime();
        try {
            Exchange exchange = ExchangeFactory.INSTANCE.createExchange(request.exchangeClass());
            MarketDataService marketData = exchange.getMarketDataService();
            Ticker ticker = marketData.getTicker(request.pair());
            OrderBook orderBook = marketData.getOrderBook(request.pair());
            long elapsedNanos = System.nanoTime() - startNanos;

            LimitOrder bestBid = first(orderBook.getBids());
            LimitOrder bestAsk = first(orderBook.getAsks());
            System.out.println("OK exchange=" + request.name()
                    + " pair=" + request.pair()
                    + " tickerBid=" + value(ticker.getBid())
                    + " tickerAsk=" + value(ticker.getAsk())
                    + " bookBid=" + price(bestBid)
                    + " bookBidSize=" + amount(bestBid)
                    + " bookAsk=" + price(bestAsk)
                    + " bookAskSize=" + amount(bestAsk)
                    + " elapsedMs=" + String.format("%.2f", elapsedNanos / 1_000_000.0));
        } catch (Throwable t) {
            long elapsedNanos = System.nanoTime() - startNanos;
            System.out.println("FAIL exchange=" + request.name()
                    + " pair=" + request.pair()
                    + " elapsedMs=" + String.format("%.2f", elapsedNanos / 1_000_000.0)
                    + " error=" + t.getClass().getSimpleName()
                    + ": " + clean(t.getMessage()));
        }
    }

    private static LimitOrder first(List<LimitOrder> orders) {
        return orders == null || orders.isEmpty() ? null : orders.get(0);
    }

    private static String price(LimitOrder order) {
        return order == null ? "NA" : value(order.getLimitPrice());
    }

    private static String amount(LimitOrder order) {
        return order == null ? "NA" : value(order.getOriginalAmount());
    }

    private static String value(BigDecimal value) {
        return value == null ? "NA" : value.stripTrailingZeros().toPlainString();
    }

    private static String clean(String message) {
        if (message == null) {
            return "no message";
        }
        return message.replace('\n', ' ').replace('\r', ' ');
    }

    private record VenueRequest(String name, Class<? extends Exchange> exchangeClass, CurrencyPair pair) {
    }
}

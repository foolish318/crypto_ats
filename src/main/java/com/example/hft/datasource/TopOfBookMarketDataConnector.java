package com.example.hft.datasource;

import com.example.hft.datasource.normalizer.TopOfBookEvent;
import com.example.hft.datasource.transport.TransportType;
import com.example.hft.exchange.CustomTopOfBookAdapter;
import com.example.hft.exchange.CustomWebSocketTopOfBookAdapter;
import com.example.hft.marketdata.model.TopOfBookSnapshot;
import java.util.Objects;
import java.util.Optional;


public final class TopOfBookMarketDataConnector implements MarketDataConnector {
    private final CustomWebSocketTopOfBookAdapter realtimeAdapter;
    private final CustomTopOfBookAdapter snapshotAdapter;
    private volatile DataSourceStatus status = DataSourceStatus.CREATED;

    public TopOfBookMarketDataConnector(CustomWebSocketTopOfBookAdapter realtimeAdapter,
                                        CustomTopOfBookAdapter snapshotAdapter) {
        this.realtimeAdapter = Objects.requireNonNull(realtimeAdapter, "realtimeAdapter");
        this.snapshotAdapter = snapshotAdapter;
    }

    @Override
    public String name() {
        return exchange() + ":" + realtimeAdapter.symbol() + ":top-of-book";
    }

    @Override
    public String exchange() {
        return realtimeAdapter.exchange();
    }

    @Override
    public String symbol() {
        return realtimeAdapter.symbol();
    }

    @Override
    public DataSourceStatus status() {
        return status;
    }

    @Override
    public void subscribe(MarketDataSubscription subscription, MarketDataSink sink) throws Exception {
        requireMatchingSubscription(subscription);
        status = DataSourceStatus.CONNECTING;
        try {
            TopOfBookSnapshot snapshot = fetchTopOfBook(subscription.symbol());
            sink.onEvent(TopOfBookEvent.from(snapshot, TransportType.WEBSOCKET));
            sink.onHealth(DataSourceHealth.live(name(), exchange(), subscription.symbol(), "top-of-book received"));
        } catch (Exception t) {
            status = DataSourceStatus.DEGRADED;
            sink.onHealth(DataSourceHealth.degraded(name(), exchange(), subscription.symbol(), clean(t.getMessage())));
            sink.onError(name(), t);
            throw t;
        }
    }

    @Override
    public TopOfBookSnapshot fetchTopOfBook(String symbol) throws Exception {
        requireMatchingSymbol(symbol);
        status = DataSourceStatus.CONNECTING;
        try {
            TopOfBookSnapshot snapshot = realtimeAdapter.fetch();
            status = DataSourceStatus.LIVE;
            return snapshot;
        } catch (Exception e) {
            status = DataSourceStatus.DEGRADED;
            throw e;
        }
    }

    @Override
    public Optional<TopOfBookSnapshot> fetchSnapshot(String symbol) throws Exception {
        requireMatchingSymbol(symbol);
        if (snapshotAdapter == null) {
            return Optional.empty();
        }
        return Optional.of(snapshotAdapter.fetch());
    }

    private void requireMatchingSubscription(MarketDataSubscription subscription) {
        if (!exchange().equals(subscription.exchange()) || !realtimeAdapter.symbol().equals(subscription.symbol())) {
            throw new IllegalArgumentException("subscription " + subscription
                    + " does not match connector " + name());
        }
    }

    private void requireMatchingSymbol(String symbol) {
        if (!realtimeAdapter.symbol().equals(symbol)) {
            throw new IllegalArgumentException("symbol " + symbol + " does not match connector " + name());
        }
    }

    private static String clean(String message) {
        return message == null ? "no message" : message.replace('\n', ' ').replace('\r', ' ');
    }
}

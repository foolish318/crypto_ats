package com.example.hft.datasource;

import com.example.hft.marketdata.model.TopOfBookSnapshot;
import java.util.Optional;


public interface MarketDataConnector extends AutoCloseable {
    String name();

    String exchange();

    String symbol();

    DataSourceStatus status();

    void subscribe(MarketDataSubscription subscription, MarketDataSink sink) throws Exception;

    TopOfBookSnapshot fetchTopOfBook(String symbol) throws Exception;

    Optional<TopOfBookSnapshot> fetchSnapshot(String symbol) throws Exception;

    @Override
    default void close() {
    }
}

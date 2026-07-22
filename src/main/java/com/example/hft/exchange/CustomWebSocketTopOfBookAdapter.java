package com.example.hft.exchange;

import com.example.hft.marketdata.model.TopOfBookSnapshot;


public interface CustomWebSocketTopOfBookAdapter {
    String exchange();

    String symbol();

    TopOfBookSnapshot fetch() throws Exception;
}

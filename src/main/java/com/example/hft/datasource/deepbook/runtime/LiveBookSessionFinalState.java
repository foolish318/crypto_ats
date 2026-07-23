package com.example.hft.datasource.deepbook.runtime;


public record LiveBookSessionFinalState(
        LiveBookSessionSnapshot session,
        LocalBookSnapshot book
) {
}
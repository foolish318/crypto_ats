#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

mvn -q -Dexec.mainClass=com.example.hft.app.BinanceRawDepthOrderBookMain -Dexec.args="record ${1:-1800} ${2:-BTCUSDT,ETHUSDT,BNBUSDT,SOLUSDT,XRPUSDT} ${3:-10} ${4:-data}" compile exec:java
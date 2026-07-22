#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

mvn -q -Dexec.mainClass=com.example.hft.app.BinanceRawDisruptorDepthMain -Dexec.args="${1:-500} ${2:-BTCUSDT,ETHUSDT,BNBUSDT,SOLUSDT,XRPUSDT} ${3:-4}" compile exec:java

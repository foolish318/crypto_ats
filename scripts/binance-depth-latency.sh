#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

mvn -q -Dexec.mainClass=com.example.hft.app.BinanceDepthLatencyMain -Dexec.args="${1:-200} ${2:-BTCUSDT,ETHUSDT,BNBUSDT,SOLUSDT,XRPUSDT} ${3:-4}" compile exec:java

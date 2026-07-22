#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

mvn -q -Dexec.mainClass=com.example.hft.app.BinanceActualLatencyMain -Dexec.args="${1:-50} ${2:-BTCUSDT,ETHUSDT,BNBUSDT} ${3:-v5-spsc} ${4:-4}" compile exec:java

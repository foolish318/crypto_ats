#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

mvn -q -Dexec.mainClass=com.example.hft.app.BinanceReplayMain -Dexec.args="${1:-10} ${2:-BTCUSDT,ETHUSDT,BNBUSDT}" compile exec:java

#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

mvn -q -Dexec.mainClass=com.example.hft.app.BinanceLivePipelineMain -Dexec.args="${1:-50} ${2:-BTCUSDT,ETHUSDT,BNBUSDT}" compile exec:java

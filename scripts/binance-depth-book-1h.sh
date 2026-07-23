#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

./scripts/binance-depth-book.sh 3600 "${1:-BTCUSDT,ETHUSDT,BNBUSDT,SOLUSDT,XRPUSDT}" "${2:-10}" "${3:-data}"
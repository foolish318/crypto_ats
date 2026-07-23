#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

if [ "$#" -lt 2 ]; then
  echo "usage: $0 <raw-jsonl> <snapshot-jsonl> [levels]" >&2
  exit 2
fi

mvn -q -Dexec.mainClass=com.example.hft.app.BinanceRawDepthOrderBookMain -Dexec.args="replay $1 $2 ${3:-10}" compile exec:java
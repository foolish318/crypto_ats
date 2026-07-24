#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

output="${1:-data/jmh-deep-book.json}"
shift || true
mkdir -p "$(dirname "$output")"

./mvnw -q -DskipTests package

java -jar target/crypto-ats-market-data-1.1.0-benchmarks.jar \
  com.example.hft.datasource.deepbook.runtime.DeepBookJmhBenchmark \
  -prof gc -rf json -rff "$output" "$@"

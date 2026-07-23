#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

output="${1:-data/jmh-deep-book-v24.json}"
shift || true
mkdir -p "$(dirname "$output")"

mvn -q -DskipTests package

java -jar target/hft-java-0.1.0-SNAPSHOT-benchmarks.jar \
  com.example.hft.datasource.deepbook.runtime.DeepBookJmhBenchmark \
  -prof gc -rf json -rff "$output" "$@"

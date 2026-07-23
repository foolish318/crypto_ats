#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

raw_file="${1:-}"
runs="${2:-3}"
output_prefix="${3:-}"

if [[ -z "$raw_file" ]]; then
  raw_file="$(find data -maxdepth 1 -type f \
    \( -name 'multi-exchange-raw-v24-*.jsonl' \
       -o -name 'multi-exchange-raw-v23-*.jsonl' \
       -o -name 'multi-exchange-raw-v22-*.jsonl' \) \
    ! -name '*.segment-*.jsonl' \
    -printf '%T@ %p\n' | sort -nr | head -1 | cut -d' ' -f2-)"
fi
if [[ -z "$raw_file" || ! -f "$raw_file" ]]; then
  echo "No raw replay file found" >&2
  exit 1
fi

args="$raw_file $runs"
if [[ -n "$output_prefix" ]]; then
  args="$args $output_prefix"
fi

mvn -q \
  -Dexec.mainClass=com.example.hft.app.FullPipelineBenchmarkMain \
  -Dexec.args="$args" \
  compile exec:java

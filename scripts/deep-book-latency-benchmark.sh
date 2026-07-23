#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

raw_file="${1:-}"
workers="${2:-4}"
target_records="${3:-500000}"
runs="${4:-5}"
output_file="${5:-}"

if [[ -z "$raw_file" ]]; then
  raw_file="$(find data -maxdepth 1 -type f \
    \( -name 'multi-exchange-raw-v24-*.jsonl' \
       -o -name 'multi-exchange-raw-v23-*.jsonl' \
       -o -name 'multi-exchange-raw-v22-*.jsonl' \
       -o -name 'multi-exchange-raw-v21-*.jsonl' \) \
    ! -name '*.segment-*.jsonl' \
    -printf '%T@ %p\n' | sort -nr | head -1 | cut -d' ' -f2-)"
fiif [[ -z "$raw_file" || ! -f "$raw_file" ]]; then
  echo "No multi-exchange raw replay file found" >&2
  exit 1
fi

args="$raw_file $workers $target_records $runs"
if [[ -n "$output_file" ]]; then
  args="$args $output_file"
fi

mvn -q \
  -Dexec.mainClass=com.example.hft.app.DeepBookLatencyBenchmarkMain \
  -Dexec.args="$args" \
  compile exec:java
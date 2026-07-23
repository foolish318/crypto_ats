#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

duration_seconds="${1:-15}"
output_dir="${2:-data}"
stale_threshold_seconds="${3:-10}"

./mvnw -q \
  -Dexec.mainClass=com.example.hft.app.MultiExchangeLocalBookMain \
  -Dexec.args="${duration_seconds} ${output_dir} ${stale_threshold_seconds}" \
  compile exec:java

#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

mvn -q -Dexec.mainClass=com.example.hft.app.BenchmarkMain -Dexec.args="${1:-200000} ${2:-4}" compile exec:java

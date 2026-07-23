#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

mvn -q -Dexec.mainClass=com.example.hft.app.DeepBookSourceDiscoveryMain -Dexec.args="${1:-data}" compile exec:java
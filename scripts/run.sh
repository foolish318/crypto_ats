#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

./scripts/multi-exchange-local-books.sh "${1:-15}" "${2:-data}" "${3:-10}"
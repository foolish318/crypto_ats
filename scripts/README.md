# Scripts

All supported commands use the pinned Maven Wrapper.

| Script | Purpose |
|---|---|
| `run.sh` | Stable alias for the live market-data runner |
| `multi-exchange-local-books.sh` | Run six live venue-local books, journal, summary, and replay parity |
| `test.sh` | Run the JUnit 5 test suite |
| `full-pipeline-benchmark.sh` | Benchmark the complete current replay path |
| `jmh-deep-book.sh` | Build and run JMH stage microbenchmarks |
| `render-architecture-diagrams.ps1` | Regenerate SVG and PNG architecture images |

Examples:

```bash
./scripts/run.sh 15 data 10
./scripts/test.sh
./scripts/full-pipeline-benchmark.sh "" 3
./scripts/jmh-deep-book.sh
```
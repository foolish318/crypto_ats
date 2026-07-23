# Project Structure

```text
hft_java/
|-- pom.xml                     Java 17 build and pinned dependencies
|-- mvnw, mvnw.cmd, .mvn/      Maven 3.9.12 wrapper
|-- scripts/                    Current run, test, benchmark, and diagram commands
|-- src/main/java/com/example/hft/
|   |-- app/                    Live and replay-benchmark entry points
|   `-- datasource/
|       |-- deepbook/           Source catalog
|       |   `-- runtime/        Protocol, books, recovery, journal, replay, benchmarks
|       |-- engine/             Cache and event bus
|       |-- instrument/         Metadata and canonical instruments
|       |-- normalizer/         Engine event contract
|       |-- book/               Book quality contract
|       `-- transport/          Transport type contract
|-- src/test/java/              JUnit 5 deterministic tests
|-- docs/                       One architecture plus per-module detail
`-- data/                       Ignored runtime artifacts; only .gitkeep is tracked
```

There are two supported application entry points:

- `MultiExchangeLocalBookMain`: live six-source capture, local books, engine, journal, summary, and replay-parity check
- `FullPipelineBenchmarkMain`: current-journal end-to-end replay benchmark

Old benchmark variants and superseded entry points are intentionally excluded from the current tree. Historical source remains available through Git history.
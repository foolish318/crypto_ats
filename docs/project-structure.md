# Project Structure

```text
hft_java/
|-- pom.xml, mvnw, .mvn/        Java 17 and pinned Maven build
|-- scripts/                    Live, test, benchmark, and diagram commands
|-- src/main/java/com/example/hft/
|   |-- app/                    Live runner and replay benchmark
|   |-- datasource/
|   |   |-- deepbook/runtime/   Book transport, validation, recovery, journal, replay
|   |   |-- engine/             Cache and event bus
|   |   `-- instrument/         Venue reference data and canonical mapping
|   `-- marketdata/
|       |-- model/              Canonical book/trade/status model
|       |-- trade/              Public-trade sources, normalizers, sessions
|       |-- api/                Immutable strategy-facing views and notifications
|       `-- recording/          Normalized recorder and deterministic replay
|-- src/test/java/              Unit, fixture, fault, replay, and API contract tests
|-- docs/                       Architecture and module details
`-- data/                       Ignored runtime journals and benchmark output
```

Supported entry points remain `MultiExchangeLocalBookMain` and `FullPipelineBenchmarkMain`.
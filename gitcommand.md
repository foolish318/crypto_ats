# Git Commands

Use this file as the single reference for pushing this project to GitHub.

## Standard Push Flow

```bash
git status --short
git add <changed-source-doc-script-files>
git commit -m "<short change summary>"
git push origin main
```

## Current Project Remote

```bash
git remote -v
git branch --show-current
```

Expected branch:

```text
main
```

Expected remote:

```text
git@github.com:foolish318/crypto_ats.git
```

## What To Add

Add source code, scripts, diagrams, and docs:

```bash
git add src scripts docs README.md runbook.md diagram.md module.md gitcommand.md .gitignore pom.xml
```

For a smaller scoped commit, list exact files instead:

```bash
git add path/to/file1 path/to/file2
```

## What Not To Add

Do not add generated live market-data captures:

```text
data/binance-raw-depth-*.jsonl
data/binance-book-events-*.jsonl
data/binance-depth-snapshots-*.jsonl
data/binance-book-summary-*.json
data/deep-book-sources-*.jsonl
target/
```

These should stay ignored by `.gitignore`.

## Last-Step Verification

```bash
git status --short
git log -1 --oneline
```

A clean pushed state should show only the latest commit line and no modified tracked files.

## V19 Push Command Used

```bash
git status --short
git add .gitignore diagram.md docs/project-structure.md module.md runbook.md scripts/deep-book-sources.sh src/main/java/com/example/hft/app/DeepBookSourceDiscoveryMain.java src/main/java/com/example/hft/datasource/DataSourceModuleVersion.java src/main/java/com/example/hft/datasource/deepbook/DeepBookSourceCatalog.java src/main/java/com/example/hft/datasource/deepbook/DeepBookSourceDefinition.java
git commit -m "Add multi-exchange deep book sources"
git push origin main
```
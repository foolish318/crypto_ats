#!/usr/bin/env python3
import re
import sys
from decimal import Decimal


def parse_log(path):
    stats = {
        "file": path,
        "version": "unknown",
        "ws_count": 0,
        "ws_load_ms": Decimal("0"),
        "engine_etl_us_count": 0,
        "engine_etl_us": Decimal("0"),
        "rest_success": 0,
        "rest_exact": 0,
        "rest_bid_bps": Decimal("0"),
        "rest_ask_bps": Decimal("0"),
        "xchange_success": 0,
        "xchange_exact": 0,
        "xchange_bid_bps": Decimal("0"),
        "xchange_ask_bps": Decimal("0"),
        "cache": "-",
        "published": "-",
        "replay": "-",
    }
    with open(path, "r", encoding="utf-8") as handle:
        for line in handle:
            version_match = re.search(r"version=([^ ]+)", line)
            if version_match and stats["version"] == "unknown":
                stats["version"] = version_match.group(1)
            if line.startswith("datasource-websocket-vs-baseline"):
                stats["version"] = "V14-connector-wrapper"

            ws_match = re.search(r"^CUSTOM_WS .* elapsedMs=([0-9.]+)", line)
            if ws_match:
                stats["ws_count"] += 1
                stats["ws_load_ms"] += Decimal(ws_match.group(1))
            etl_match = re.search(r"engineEtlUs=([0-9.]+)", line)
            if etl_match:
                stats["engine_etl_us_count"] += 1
                stats["engine_etl_us"] += Decimal(etl_match.group(1))

            compare_match = re.search(
                r"^(WS_VS_REST|WS_VS_XCHANGE) COMPARE .* "
                r"bidDiff=([^ ]+) bidDiffBps=([^ ]+) askDiff=([^ ]+) askDiffBps=([^ ]+)",
                line,
            )
            if compare_match:
                kind, bid_diff, bid_bps, ask_diff, ask_bps = compare_match.groups()
                exact = Decimal(bid_diff) == 0 and Decimal(ask_diff) == 0
                if kind == "WS_VS_REST":
                    stats["rest_success"] += 1
                    stats["rest_bid_bps"] += Decimal(bid_bps)
                    stats["rest_ask_bps"] += Decimal(ask_bps)
                    if exact:
                        stats["rest_exact"] += 1
                else:
                    stats["xchange_success"] += 1
                    stats["xchange_bid_bps"] += Decimal(bid_bps)
                    stats["xchange_ask_bps"] += Decimal(ask_bps)
                    if exact:
                        stats["xchange_exact"] += 1

            summary_match = re.search(
                r"DATASOURCE_ENGINE_SUMMARY .* cacheTopOfBook=([0-9]+) "
                r"publishedEvents=([0-9]+) replayRecords=([0-9]+)",
                line,
            )
            if summary_match:
                stats["cache"], stats["published"], stats["replay"] = summary_match.groups()
    return stats


def avg(total, count):
    return Decimal("0") if count == 0 else total / Decimal(count)


def fmt(value):
    return f"{value:.6f}"


def print_table(rows):
    print(
        "version,file,wsCount,avgWsLoadMs,avgEngineEtlUs,"
        "restSuccess,restExact,restAvgBidBps,restAvgAskBps,"
        "xchangeSuccess,xchangeExact,xchangeAvgBidBps,xchangeAvgAskBps,"
        "cacheTopOfBook,publishedEvents,replayRecords"
    )
    for row in rows:
        print(
            ",".join(
                [
                    row["version"],
                    row["file"],
                    str(row["ws_count"]),
                    fmt(avg(row["ws_load_ms"], row["ws_count"])),
                    fmt(avg(row["engine_etl_us"], row["engine_etl_us_count"])),
                    str(row["rest_success"]),
                    str(row["rest_exact"]),
                    fmt(avg(row["rest_bid_bps"], row["rest_success"])),
                    fmt(avg(row["rest_ask_bps"], row["rest_success"])),
                    str(row["xchange_success"]),
                    str(row["xchange_exact"]),
                    fmt(avg(row["xchange_bid_bps"], row["xchange_success"])),
                    fmt(avg(row["xchange_ask_bps"], row["xchange_success"])),
                    str(row["cache"]),
                    str(row["published"]),
                    str(row["replay"]),
                ]
            )
        )


def main():
    if len(sys.argv) < 2:
        print("usage: scripts/compare-datasource-logs.py <log> [<log> ...]", file=sys.stderr)
        return 2
    print_table([parse_log(path) for path in sys.argv[1:]])
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

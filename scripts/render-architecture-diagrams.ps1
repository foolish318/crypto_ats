param(
    [switch]$SkipPng
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$docsRoot = Join-Path $repoRoot 'docs'
$modulesRoot = Join-Path $docsRoot 'modules'
$utf8 = New-Object System.Text.UTF8Encoding($false)

New-Item -ItemType Directory -Force -Path $modulesRoot | Out-Null

$palette = @{
    source    = @{ fill = '#e0f2fe'; stroke = '#0284c7'; title = '#075985' }
    connector = @{ fill = '#fef3c7'; stroke = '#d97706'; title = '#92400e' }
    intake    = @{ fill = '#f1f5f9'; stroke = '#64748b'; title = '#334155' }
    quality   = @{ fill = '#ffe4e6'; stroke = '#e11d48'; title = '#9f1239' }
    book      = @{ fill = '#ede9fe'; stroke = '#7c3aed'; title = '#5b21b6' }
    engine    = @{ fill = '#ccfbf1'; stroke = '#0f766e'; title = '#115e59' }
    consumer  = @{ fill = '#dcfce7'; stroke = '#16a34a'; title = '#166534' }
    replay    = @{ fill = '#fae8ff'; stroke = '#a21caf'; title = '#86198f' }
    future    = @{ fill = '#f8fafc'; stroke = '#94a3b8'; title = '#475569' }
}

function Escape-Xml([string]$value) {
    return [System.Security.SecurityElement]::Escape($value)
}

function New-SvgBuilder([int]$width, [int]$height, [string]$title, [string]$description) {
    $builder = [System.Text.StringBuilder]::new()
    [void]$builder.AppendLine("<svg xmlns=`"http://www.w3.org/2000/svg`" width=`"$width`" height=`"$height`" viewBox=`"0 0 $width $height`" role=`"img`" aria-labelledby=`"title desc`">")
    [void]$builder.AppendLine("  <title id=`"title`">$(Escape-Xml $title)</title>")
    [void]$builder.AppendLine("  <desc id=`"desc`">$(Escape-Xml $description)</desc>")
    [void]$builder.AppendLine('  <defs>')
    [void]$builder.AppendLine('    <marker id="arrow" markerWidth="12" markerHeight="12" refX="10" refY="6" orient="auto" markerUnits="strokeWidth"><path d="M1 1 L11 6 L1 11 Z" fill="#475569"/></marker>')
    [void]$builder.AppendLine('    <marker id="failure-arrow" markerWidth="12" markerHeight="12" refX="10" refY="6" orient="auto" markerUnits="strokeWidth"><path d="M1 1 L11 6 L1 11 Z" fill="#be123c"/></marker>')
    [void]$builder.AppendLine('    <marker id="replay-arrow" markerWidth="12" markerHeight="12" refX="10" refY="6" orient="auto" markerUnits="strokeWidth"><path d="M1 1 L11 6 L1 11 Z" fill="#a21caf"/></marker>')
    [void]$builder.AppendLine('    <filter id="shadow" x="-15%" y="-15%" width="130%" height="140%"><feDropShadow dx="0" dy="3" stdDeviation="5" flood-color="#0f172a" flood-opacity="0.10"/></filter>')
    [void]$builder.AppendLine('  </defs>')
    [void]$builder.AppendLine("  <rect width=`"$width`" height=`"$height`" fill=`"#f8fafc`"/>")
    return $builder
}

function Add-Text(
    [System.Text.StringBuilder]$builder,
    [int]$x,
    [int]$y,
    [string]$text,
    [int]$size = 15,
    [string]$weight = '400',
    [string]$fill = '#334155',
    [string]$anchor = 'start'
) {
    [void]$builder.AppendLine("  <text x=`"$x`" y=`"$y`" font-family=`"Arial, sans-serif`" font-size=`"$size`" font-weight=`"$weight`" fill=`"$fill`" text-anchor=`"$anchor`">$(Escape-Xml $text)</text>")
}

function Add-Node(
    [System.Text.StringBuilder]$builder,
    [int]$x,
    [int]$y,
    [int]$width,
    [int]$height,
    [string]$title,
    [string[]]$lines,
    [string]$kind,
    [string]$badge = ''
) {
    $colors = $palette[$kind]
    $dash = if ($kind -eq 'future') { ' stroke-dasharray="8 6"' } else { '' }
    [void]$builder.AppendLine("  <g filter=`"url(#shadow)`">")
    [void]$builder.AppendLine("    <rect x=`"$x`" y=`"$y`" width=`"$width`" height=`"$height`" rx=`"7`" fill=`"$($colors.fill)`" stroke=`"$($colors.stroke)`" stroke-width=`"2`"$dash/>")
    Add-Text $builder ($x + 20) ($y + 34) $title 19 '700' $colors.title
    $lineY = $y + 62
    foreach ($line in $lines) {
        Add-Text $builder ($x + 20) $lineY $line 13 '400' '#475569'
        $lineY += 22
    }
    if ($badge) {
        $badgeWidth = [Math]::Max(74, ($badge.Length * 7) + 20)
        [void]$builder.AppendLine("    <rect x=`"$($x + $width - $badgeWidth - 12)`" y=`"$($y + 12)`" width=`"$badgeWidth`" height=`"23`" rx=`"4`" fill=`"#ffffff`" stroke=`"$($colors.stroke)`" stroke-opacity=`"0.45`"/>")
        Add-Text $builder ($x + $width - ($badgeWidth / 2) - 12) ($y + 28) $badge 10 '700' $colors.title 'middle'
    }
    [void]$builder.AppendLine('  </g>')
}

function Add-Path(
    [System.Text.StringBuilder]$builder,
    [string]$path,
    [string]$kind = 'normal',
    [string]$label = '',
    [int]$labelX = 0,
    [int]$labelY = 0
) {
    $stroke = '#475569'
    $marker = 'arrow'
    $dash = ''
    if ($kind -eq 'failure') {
        $stroke = '#be123c'
        $marker = 'failure-arrow'
    } elseif ($kind -eq 'replay') {
        $stroke = '#a21caf'
        $marker = 'replay-arrow'
        $dash = ' stroke-dasharray="10 8"'
    }
    [void]$builder.AppendLine("  <path d=`"$path`" fill=`"none`" stroke=`"$stroke`" stroke-width=`"3`" stroke-linecap=`"round`" stroke-linejoin=`"round`"$dash marker-end=`"url(#$marker)`"/>")
    if ($label) {
        Add-Text $builder $labelX $labelY $label 12 '700' $stroke 'middle'
    }
}

function Save-Svg([System.Text.StringBuilder]$builder, [string]$path) {
    [void]$builder.AppendLine('</svg>')
    $content = $builder.ToString().Replace("`r`n", "`n")
    [System.IO.File]::WriteAllText($path, $content, $utf8)
}

function Write-CanonicalDiagram {
    $b = New-SvgBuilder 1900 1230 'Reference-inspired crypto ATS market-data architecture' 'A V24 direct single-writer deep-book pipeline with availability invalidation, canonical consolidation, bounded listeners, and journal replay.'
    Add-Text $b 55 58 'Crypto ATS Market-Data Architecture' 34 '700' '#0f172a'
    Add-Text $b 55 91 'One canonical pipeline, informed by established open-source connector and data-engine patterns.' 16 '400' '#475569'

    Add-Node $b 55 125 560 124 'Hummingbot pattern' @('Connector owns venue lifecycle and subscriptions', 'Separate order-book data source, tracker, and state') 'connector' 'reference'
    Add-Node $b 670 125 560 124 'NautilusTrader pattern' @('Adapters normalize venue APIs into domain events', 'Data engine updates cache before publishing to bus') 'engine' 'reference'
    Add-Node $b 1285 125 560 124 'XChange / CCXT pattern' @('Unified facade over venue-specific implementations', 'Common contracts keep exchange details behind adapters') 'source' 'reference'

    $columns = @(
        @{ x = 40; title = '1  SOURCES'; fill = '#f0f9ff' },
        @{ x = 350; title = '2  CONNECTORS'; fill = '#fffbeb' },
        @{ x = 660; title = '3  INTAKE'; fill = '#f8fafc' },
        @{ x = 970; title = '4  INTEGRITY + STATE'; fill = '#faf5ff' },
        @{ x = 1280; title = '5  DISTRIBUTION'; fill = '#f0fdfa' },
        @{ x = 1590; title = '6  CONSUMERS'; fill = '#f0fdf4' }
    )
    foreach ($column in $columns) {
        [void]$b.AppendLine("  <rect x=`"$($column.x)`" y=`"294`" width=`"270`" height=`"810`" rx=`"8`" fill=`"$($column.fill)`" stroke=`"#cbd5e1`"/>")
        Add-Text $b ($column.x + 18) 329 $column.title 14 '700' '#334155'
    }

    Add-Node $b 55 355 240 112 'Direct Exchanges' @('Binance.US / OKX / Kraken', 'REST + WebSocket; FIX later') 'source'
    Add-Node $b 55 520 240 112 'Third-Party Feeds' @('normalized feed or FIX gateway', 'future source implementation') 'source' 'planned'
    Add-Node $b 55 685 240 112 'Replay Store' @('recorded raw market data', 'deterministic source') 'replay'

    Add-Node $b 365 355 240 112 'InstrumentProvider' @('public status + tick / lot rules', 'fail-closed canonical mapping') 'connector' 'V22'
    Add-Node $b 365 520 240 112 'MarketDataConnector' @('connect, subscribe, health', 'venue-specific lifecycle') 'connector'
    Add-Node $b 365 685 240 112 'MarketDataClient' @('snapshot and stream ownership', 'reconnect boundaries') 'connector'

    Add-Node $b 675 355 240 112 'Venue Protocol' @('subscribe ACK + errors', 'ping / pong + connection age') 'intake' 'V22'
    Add-Node $b 675 520 240 112 'Ingress RawEnvelope' @('record before async handoff', 'source + generation + clocks') 'intake'
    Add-Node $b 675 685 240 112 'Source Book Pipeline' @('inline protocol + book apply', 'single writer; no worker queue') 'intake' 'V24'

    Add-Node $b 985 355 240 112 'Parse + Quality Gate' @('schema, exact values, freshness', 'sequence, checksum, crossed book') 'quality' 'V22'
    Add-Node $b 985 520 240 112 'Session Health + Recovery' @('transport / book / session state', 'watchdog + backoff + generation') 'book'
    Add-Node $b 985 685 240 112 'Venue Book Builder' @('stale / gap / checksum rules', 'exact-decimal atomic updates') 'book'
    Add-Node $b 985 850 240 112 'Venue-Local Books' @('six independent deep books', 'publishable only when LIVE') 'book' 'V22'

    Add-Node $b 1295 355 240 112 'AcceptedLocalBookEvent' @('canonical id + generation', 'quality-approved immutable depth') 'engine' 'V24'
    Add-Node $b 1295 520 240 112 'MarketDataEngine' @('single accepted-event boundary', 'cache first, publish second') 'engine'
    Add-Node $b 1295 685 240 112 'Fenced Cache + EventBus' @('tombstone on unavailable state', 'inline core + bounded side output') 'engine' 'V24'
    Add-Node $b 1295 850 240 112 'Segmented Raw Journal' @('headers + checksums + index', 'drop makes replay unsafe') 'replay' 'V24'

    Add-Node $b 1605 355 240 112 'Consolidated Book' @('canonical NBBO + venue depth', 'freshness + watermark/coherence') 'source' 'V24'
    Add-Node $b 1605 520 240 112 'Strategy Pipeline' @('spread and depth decisions', 'market-data consumers') 'consumer'
    Add-Node $b 1605 685 240 112 'Journal + Replay Check' @('bounded queue + lag metrics', 'streaming final-book parity') 'replay'
    Add-Node $b 1605 850 240 112 'Order / Risk' @('execution lifecycle is separate', 'not implemented in this scope') 'future' 'future'

    Add-Path $b 'M295 411 H365'
    Add-Path $b 'M295 576 H330 V576 H365'
    Add-Path $b 'M295 741 H330 V576 H365'
    Add-Path $b 'M485 467 V520'
    Add-Path $b 'M485 632 V685'
    Add-Path $b 'M605 741 H640 V411 H675'
    Add-Path $b 'M795 467 V520'
    Add-Path $b 'M795 632 V685'
    Add-Path $b 'M915 741 H945 V411 H985'
    Add-Path $b 'M1105 467 V520'
    Add-Path $b 'M1105 632 V685'
    Add-Path $b 'M1105 797 V850'
    Add-Path $b 'M1225 906 H1255 V411 H1295'
    Add-Path $b 'M1415 467 V520'
    Add-Path $b 'M1415 632 V685'
    Add-Path $b 'M1535 741 H1565 V411 H1605'
    Add-Path $b 'M1535 741 H1565 V576 H1605'
    Add-Path $b 'M1535 741 H1605'
    Add-Path $b 'M915 576 H940 V1055 H1415 V962' 'replay' 'raw lifecycle evidence' 1165 1045

    Add-Path $b 'M985 411 H950 V1015 H1105 V962' 'failure' 'quality failure -> recover and rebuild' 1080 1040
    Add-Path $b 'M1295 906 H1260 V1070 H175 V797' 'replay' 'recorded input returns through the same connector contract' 720 1092

    [void]$b.AppendLine('  <rect x="55" y="1135" width="1790" height="58" rx="7" fill="#ffffff" stroke="#cbd5e1"/>')
    Add-Text $b 82 1170 'Design rule: downstream code consumes accepted canonical state and is independent of REST, WebSocket, FIX, third-party, or replay transport.' 14 '700' '#334155'
    Save-Svg $b (Join-Path $docsRoot 'architecture.svg')
}

function Write-ModuleDiagram(
    [string]$fileName,
    [string]$title,
    [string]$subtitle,
    [object[]]$nodes,
    [object[]]$paths,
    [string]$rule
) {
    $b = New-SvgBuilder 1500 760 $title $subtitle
    Add-Text $b 55 58 $title 32 '700' '#0f172a'
    Add-Text $b 55 89 $subtitle 15 '400' '#475569'
    [void]$b.AppendLine('  <rect x="40" y="130" width="1420" height="500" rx="8" fill="#ffffff" stroke="#cbd5e1"/>')
    foreach ($node in $nodes) {
        Add-Node $b $node.x $node.y $node.w $node.h $node.title $node.lines $node.kind $node.badge
    }
    foreach ($path in $paths) {
        Add-Path $b $path.d $path.kind $path.label $path.labelX $path.labelY
    }
    [void]$b.AppendLine('  <rect x="55" y="660" width="1390" height="58" rx="7" fill="#f8fafc" stroke="#cbd5e1"/>')
    Add-Text $b 82 695 $rule 14 '700' '#334155'
    Save-Svg $b (Join-Path $modulesRoot "$fileName.svg")
}

function N($x, $y, $w, $h, $title, $lines, $kind, $badge = '') {
    return @{ x = $x; y = $y; w = $w; h = $h; title = $title; lines = $lines; kind = $kind; badge = $badge }
}

function P($d, $kind = 'normal', $label = '', $labelX = 0, $labelY = 0) {
    return @{ d = $d; kind = $kind; label = $label; labelX = $labelX; labelY = $labelY }
}

Write-CanonicalDiagram

Write-ModuleDiagram 'source-connector' 'Source and Connector Module' 'Venue-specific connectivity behind stable Java contracts.' @(
    (N 70 205 240 112 'Exchange Endpoint' @('REST snapshot', 'WebSocket stream') 'source'),
    (N 370 205 240 112 'InstrumentProvider' @('venue symbols', 'canonical metadata') 'connector'),
    (N 670 205 240 112 'MarketDataConnector' @('connect + subscribe', 'status + health') 'connector'),
    (N 970 205 240 112 'MarketDataClient' @('owns live session', 'delivers raw messages') 'connector'),
    (N 1170 430 240 112 'DataSourceHealth' @('CONNECTING / LIVE', 'DEGRADED / STOPPED') 'quality')
) @(
    (P 'M310 261 H370'),
    (P 'M610 261 H670'),
    (P 'M910 261 H970'),
    (P 'M1090 317 V486 H1170'),
    (P 'M1170 515 H940 V350 H790 V317' 'failure' 'status feedback' 1055 370)
) 'Rule: adding an exchange changes an adapter, not downstream market-data consumers.'

Write-ModuleDiagram 'transport-intake' 'Transport and Raw Intake Module' 'Injectable live I/O and bounded evidence capture before direct processing.' @(
    (N 70 205 230 112 'VenueTransport' @('JDK WebSocket live adapter', 'deterministic fake in tests') 'source' 'V24'),
    (N 345 205 230 112 'SnapshotProvider' @('JDK HTTP snapshot adapter', 'delayed/failing fake in tests') 'source' 'V24'),
    (N 620 205 230 112 'RawEnvelope' @('payload + lifecycle', 'source + generation + clocks') 'intake'),
    (N 895 205 250 112 'AsyncRawRecorder' @('bounded queue + drop state', 'writer never blocks book') 'replay'),
    (N 1190 205 230 112 'Segmented Journal' @('header + checksum + index', 'rotation + fsync policy') 'replay' 'V24'),
    (N 620 430 250 112 'Direct Book Handoff' @('protocol then source book', 'one sequential writer') 'quality' 'default')
) @(
    (P 'M300 261 H620'),
    (P 'M575 261 H620'),
    (P 'M850 261 H895'),
    (P 'M1145 261 H1190'),
    (P 'M745 317 V430'),
    (P 'M895 486 H925 V350 H1020 V317' 'failure' 'bounded pressure metrics' 1040 390)
) 'Rule: capture complete raw evidence, then process inline; every side-output queue is bounded and observable.'

Write-ModuleDiagram 'parser-normalizer' 'Parser and Normalizer Module' 'Translate venue payloads into canonical Java market-data events.' @(
    (N 70 205 240 112 'RawInboundMessage' @('payload and metadata', 'no venue state mutation') 'intake'),
    (N 370 205 240 112 'Venue Parser' @('Binance / OKX / Kraken', 'schema-specific decoding') 'connector'),
    (N 670 205 240 112 'Value Conversion' @('exact decimal price/size', 'timestamp and sequence') 'intake'),
    (N 970 205 240 112 'Canonical Event' @('TopOfBookEvent', 'BookDepthEvent / TradeEvent') 'engine'),
    (N 370 430 240 112 'Parse Failure' @('reason + source offset', 'count and quarantine') 'quality'),
    (N 970 430 240 112 'SymbolMapper' @('venue symbol', 'canonical instrument id') 'connector')
) @(
    (P 'M310 261 H370'),
    (P 'M610 261 H670'),
    (P 'M910 261 H970'),
    (P 'M490 317 V430' 'failure' 'invalid payload' 565 390),
    (P 'M1090 430 V317'),
    (P 'M1210 486 H1260 V350 H1090 V317')
) 'Rule: canonical events retain source timestamps and sequence metadata required by quality checks.'

Write-ModuleDiagram 'data-quality' 'Data Quality Gate Module' 'Validate common invariants and venue-specific continuity before publication.' @(
    (N 70 205 230 112 'Canonical Event' @('snapshot or update', 'timestamps + sequence') 'engine'),
    (N 345 205 230 112 'Common Checks' @('schema, price, quantity', 'freshness + crossed book') 'quality'),
    (N 620 205 230 112 'Venue Checks' @('Binance U/u, OKX seq', 'Kraken CRC32') 'quality'),
    (N 895 205 230 112 'Quality Report' @('accepted / rejected', 'reason + counters') 'quality'),
    (N 1170 205 230 112 'Accepted Event' @('eligible for book state', 'quality metadata retained') 'book'),
    (N 895 430 230 112 'Recovery Request' @('degrade affected book', 'resnapshot or reconnect') 'quality')
) @(
    (P 'M300 261 H345'),
    (P 'M575 261 H620'),
    (P 'M850 261 H895'),
    (P 'M1125 261 H1170'),
    (P 'M1010 317 V430' 'failure' 'REJECT' 1060 390)
) 'Rule: transport success is not data quality; only ACCEPT may mutate publishable state.'

Write-ModuleDiagram 'order-book' 'Venue-Local Order-Book Module' 'Maintain one independently validated book for each exchange and symbol.' @(
    (N 70 205 230 112 'Venue Snapshot' @('REST for Binance.US', 'WS for OKX / Kraken') 'source'),
    (N 345 205 230 112 'Stream Updates' @('all messages, no sampling', 'source receive timestamp') 'intake'),
    (N 620 205 230 112 'Venue Builder' @('Binance U/u; OKX seq', 'Kraken timestamp + CRC32') 'book' 'V22'),
    (N 895 205 230 112 'Exact Decimal Book' @('apply/delete levels', 'detect stale/gap/cross') 'book'),
    (N 1170 205 230 112 'AcceptedLocalBookEvent' @('LIVE depth snapshot', 'generation + receive clocks') 'engine'),
    (N 895 430 230 112 'DEGRADED Book' @('publication suppressed', 'recovery required') 'quality')
) @(
    (P 'M185 205 V170 H735 V205'),
    (P 'M575 261 H620'),
    (P 'M850 261 H895'),
    (P 'M1125 261 H1170'),
    (P 'M1010 317 V430' 'failure' 'gap / checksum / cross' 1125 390)
) 'Rule: only accepted, fresh LIVE state crosses from a venue-local book into MarketDataEngine.'

Write-ModuleDiagram 'recovery' 'Recovery And Availability Module' 'Withdraw invalid state immediately, then rebuild behind a generation fence.' @(
    (N 70 205 230 112 'Session Health' @('transport / book / session', 'freshness predicate') 'quality'),
    (N 345 205 230 112 'BookAvailabilityEvent' @('STALE / RECOVERING', 'DISCONNECTED / INVALID') 'quality' 'V24'),
    (N 620 205 230 112 'Cache Tombstone' @('remove stale venue now', 'block old generation') 'engine' 'P0'),
    (N 895 205 230 112 'RecoveryCoordinator' @('300ms exponential backoff', 'jitter + 30s cap') 'connector'),
    (N 1170 205 230 112 'New Generation' @('snapshot / bridge', 'continuity + quality') 'book'),
    (N 1170 430 230 112 'Accepted LIVE' @('restore cache and venue', 'reset recovery backoff') 'consumer')
) @(
    (P 'M300 261 H345'),
    (P 'M575 261 H620'),
    (P 'M850 261 H895'),
    (P 'M1125 261 H1170'),
    (P 'M1285 317 V430'),
    (P 'M1170 486 H1080 V590 H460 V317' 'failure' 'retry; old callbacks rejected' 770 612)
) 'Rule: a health-only LIVE signal cannot restore a book; only a quality-approved accepted generation can.'

Write-ModuleDiagram 'data-engine' 'Market-Data Engine Module' 'Cache-first accepted publication plus immediate generation-safe invalidation.' @(
    (N 70 185 230 112 'Accepted Book' @('canonical id + generation', 'immutable LIVE depth') 'book'),
    (N 70 405 230 112 'Availability Event' @('stale / recover / stop', 'reason + observed time') 'quality'),
    (N 370 265 240 112 'MarketDataEngine' @('accepted + health + error', 'single domain boundary') 'engine' 'V24'),
    (N 680 185 240 112 'Fenced Cache' @('update or tombstone', 'reject old generation') 'engine'),
    (N 680 405 240 112 'EventBus' @('core inline ordering', 'bounded async channels') 'engine'),
    (N 990 185 240 112 'Consolidated View' @('canonical NBBO', 'freshness + coherence') 'source'),
    (N 990 405 240 112 'Strategy + Side Output' @('inline deterministic strategy', 'async recorder / analytics') 'consumer')
) @(
    (P 'M300 241 H335 V321 H370'),
    (P 'M300 461 H335 V321 H370'),
    (P 'M610 321 H650 V241 H680'),
    (P 'M610 321 H650 V461 H680'),
    (P 'M920 241 H990'),
    (P 'M920 461 H990')
) 'Rule: accepted data restores state; unavailable data tombstones it; no old generation reaches a consumer.'

Write-ModuleDiagram 'recorder-replay' 'Recorder And Replay Journal Module' 'Bounded long-running capture with checksummed streaming reconstruction.' @(
    (N 70 205 230 112 'RawEnvelope' @('snapshot / WS / lifecycle', 'generation + receive clocks') 'source'),
    (N 345 205 230 112 'Bounded Recorder' @('queue depth + write lag', 'drop => replaySafe false') 'replay'),
    (N 620 205 230 112 'Journal Segment' @('versioned metadata header', 'SHA-256 record frames') 'replay' 'V24'),
    (N 895 205 230 112 'Index + Cursor' @('frame ranges + file order', 'rotation / retention / disk') 'replay'),
    (N 1170 205 230 112 'Streaming Replay' @('line-by-line incremental', 'tail + checksum validation') 'engine'),
    (N 895 430 230 112 'Parity Check' @('sequence + quality + BBO', 'retained depth equality') 'intake')
) @(
    (P 'M300 261 H345'),
    (P 'M575 261 H620'),
    (P 'M850 261 H895'),
    (P 'M1125 261 H1170'),
    (P 'M1285 317 V390 H1010 V430'),
    (P 'M895 486 H760 V590 H185 V317' 'replay' 'same protocol + builder path' 540 612)
) 'Rule: accepted recorder loss is never hidden; only checksummed replay-safe evidence may claim parity.'

Write-ModuleDiagram 'cross-exchange-view' 'Consolidated Cross-Exchange Book' 'Aggregate canonical instruments while preserving venue state and time consistency.' @(
    (N 70 185 230 112 'Binance.US Book' @('BTCUSDT + generation', 'LIVE depth + clocks') 'book'),
    (N 70 345 230 112 'OKX Book' @('BTC-USDT + generation', 'LIVE depth + clocks') 'book'),
    (N 70 505 230 112 'Kraken Book' @('BTC/USD + generation', 'LIVE depth + clocks') 'book'),
    (N 430 265 250 112 'Canonical Instrument' @('map venue symbols', 'BTC-USD identity') 'connector'),
    (N 780 265 250 112 'Eligibility Filter' @('state + generation + age', 'exclude stale venue now') 'quality'),
    (N 1130 265 260 112 'Consolidated Snapshot' @('best bid/ask venue + spread', 'locked/crossed + venue count') 'source' 'V24'),
    (N 1130 465 260 112 'Time Consistency' @('watermark + max skew', 'explicit coherent flag') 'consumer')
) @(
    (P 'M300 241 H370 V321 H430'),
    (P 'M300 401 H430'),
    (P 'M300 561 H370 V321 H430'),
    (P 'M680 321 H780'),
    (P 'M1030 321 H1130'),
    (P 'M1260 377 V465')
) 'Rule: NBBO contains only LIVE fresh generations; asynchronous venue times remain explicit.'

Write-ModuleDiagram 'strategy-benchmark' 'Strategy And Performance Baselines' 'Separate deterministic decisions, side-output pressure, micro-costs, and full-path latency.' @(
    (N 70 205 230 112 'Accepted / Consolidated' @('immutable LIVE snapshot', 'canonical venue attribution') 'source'),
    (N 345 205 230 112 'Core Strategy' @('inline deterministic listener', 'no network or database I/O') 'consumer'),
    (N 620 205 230 112 'Bounded Side Output' @('recorder / analytics', 'depth + lag + drops') 'replay'),
    (N 895 205 230 112 'JMH Stages' @('classify / parse / mutate', 'snapshot / publish / cache') 'intake'),
    (N 1170 205 230 112 'Full Pipeline Replay' @('p50 to p99.9 + max', 'allocation + GC + parity') 'intake' 'V24'),
    (N 620 430 230 112 'Capacity Experiment' @('direct vs partitioned', 'not the live pipeline') 'future')
) @(
    (P 'M300 261 H345'),
    (P 'M575 261 H620'),
    (P 'M850 261 H895'),
    (P 'M1125 261 H1170'),
    (P 'M735 317 V430'),
    (P 'M850 486 H1010 V317')
) 'Rule: keep DIRECT_SINGLE_WRITER until complete-path evidence proves another design meets the budget.'

if (-not $SkipPng) {
    $chrome = 'C:\Program Files\Google\Chrome\Application\chrome.exe'
    if (-not (Test-Path -LiteralPath $chrome)) {
        throw "Chrome not found at $chrome. Run with -SkipPng to generate SVG only."
    }
    $svgFiles = @((Join-Path $docsRoot 'architecture.svg')) + @(Get-ChildItem -LiteralPath $modulesRoot -Filter '*.svg' | Select-Object -ExpandProperty FullName)
    foreach ($svg in $svgFiles) {
        $png = [System.IO.Path]::ChangeExtension($svg, '.png')
        $uri = [System.Uri]::new($svg).AbsoluteUri
        $isCanonical = [System.IO.Path]::GetFileName($svg) -eq 'architecture.svg'
        $windowSize = if ($isCanonical) { '1900,1230' } else { '1500,760' }
        & $chrome '--headless=new' '--disable-gpu' '--hide-scrollbars' "--window-size=$windowSize" "--screenshot=$png" $uri | Out-Null
        if ($LASTEXITCODE -ne 0) {
            throw "Chrome failed to render $svg"
        }
    }
}

Write-Output 'Architecture diagrams rendered.'

package com.example.hft.datasource.instrument;

import com.example.hft.datasource.deepbook.DeepBookSourceDefinition;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;


public final class VenueInstrumentMetadataLoader {
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient httpClient;
    private final ObjectMapper mapper;

    public VenueInstrumentMetadataLoader(HttpClient httpClient, ObjectMapper mapper) {
        this.httpClient = httpClient;
        this.mapper = mapper;
    }

    public Map<String, Instrument> loadAll(List<DeepBookSourceDefinition> sources)
            throws Exception {
        Map<String, Instrument> instruments = new LinkedHashMap<>();
        for (DeepBookSourceDefinition source : sources) {
            instruments.put(source.id(), load(source));
        }
        return Map.copyOf(instruments);
    }

    public Instrument load(DeepBookSourceDefinition source) throws Exception {
        return switch (source.exchange()) {
            case "BINANCE_US" -> loadBinanceUs(source);
            case "OKX" -> loadOkx(source);
            case "KRAKEN" -> loadKraken(source);
            default -> throw new IllegalArgumentException(
                    "unsupported instrument metadata venue " + source.exchange());
        };
    }

    private Instrument loadBinanceUs(DeepBookSourceDefinition source) throws Exception {
        JsonNode root = get(URI.create(
                "https://api.binance.us/api/v3/exchangeInfo?symbol="
                        + encode(source.symbol())));
        JsonNode symbol = requiredFirst(root.path("symbols"), "Binance symbols");
        requireText(symbol, "symbol", source.symbol());
        requireText(symbol, "status", "TRADING");
        BigDecimal tickSize = filterValue(symbol.path("filters"), "PRICE_FILTER", "tickSize");
        BigDecimal lotSize = filterValue(symbol.path("filters"), "LOT_SIZE", "stepSize");
        String base = requiredText(symbol, "baseAsset");
        String quote = requiredText(symbol, "quoteAsset");
        return instrument(source, base, quote, tickSize, lotSize);
    }

    private Instrument loadOkx(DeepBookSourceDefinition source) throws Exception {
        JsonNode root = get(URI.create(
                "https://www.okx.com/api/v5/public/instruments?instType=SPOT&instId="
                        + encode(source.symbol())));
        if (!"0".equals(root.path("code").asText())) {
            throw new IllegalArgumentException("OKX metadata error: " + root.path("msg").asText());
        }
        JsonNode instrument = requiredFirst(root.path("data"), "OKX data");
        requireText(instrument, "instId", source.symbol());
        requireText(instrument, "state", "live");
        return instrument(
                source,
                requiredText(instrument, "baseCcy"),
                requiredText(instrument, "quoteCcy"),
                positiveDecimal(instrument, "tickSz"),
                positiveDecimal(instrument, "lotSz")
        );
    }

    private Instrument loadKraken(DeepBookSourceDefinition source) throws Exception {
        String restPair = source.symbol().replace("/", "");
        JsonNode root = get(URI.create(
                "https://api.kraken.com/0/public/AssetPairs?pair=" + encode(restPair)));
        if (!root.path("error").isArray() || !root.path("error").isEmpty()) {
            throw new IllegalArgumentException("Kraken metadata error: " + root.path("error"));
        }
        JsonNode result = root.path("result");
        if (!result.isObject() || result.isEmpty()) {
            throw new IllegalArgumentException("Kraken metadata result is empty");
        }
        JsonNode pair = result.elements().next();
        requireText(pair, "status", "online");
        String[] sourceParts = source.symbol().split("/", 2);
        if (sourceParts.length != 2) {
            throw new IllegalArgumentException("Kraken symbol must contain '/'");
        }
        int lotDecimals = pair.path("lot_decimals").asInt(-1);
        if (lotDecimals < 0 || lotDecimals > 18) {
            throw new IllegalArgumentException("invalid Kraken lot_decimals " + lotDecimals);
        }
        return instrument(
                source,
                sourceParts[0],
                sourceParts[1],
                positiveDecimal(pair, "tick_size"),
                BigDecimal.ONE.movePointLeft(lotDecimals)
        );
    }

    private JsonNode get(URI uri) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );
        if (response.statusCode() / 100 != 2) {
            throw new IllegalArgumentException(
                    "metadata HTTP " + response.statusCode() + " from " + uri.getHost());
        }
        return mapper.readTree(response.body());
    }

    private static Instrument instrument(
            DeepBookSourceDefinition source,
            String base,
            String quote,
            BigDecimal tickSize,
            BigDecimal lotSize
    ) {
        String normalizedBase = normalizeAsset(base);
        String normalizedQuote = normalizeAsset(quote);
        return new Instrument(
                source.exchange(),
                source.symbol(),
                normalizedBase + "-" + normalizedQuote,
                normalizedBase,
                normalizedQuote,
                tickSize,
                lotSize
        );
    }

    private static String normalizeAsset(String asset) {
        String normalized = asset.toUpperCase(Locale.ROOT);
        return "XBT".equals(normalized) ? "BTC" : normalized;
    }

    private static BigDecimal filterValue(
            JsonNode filters,
            String filterType,
            String field
    ) {
        if (filters.isArray()) {
            for (JsonNode filter : filters) {
                if (filterType.equals(filter.path("filterType").asText())) {
                    return positiveDecimal(filter, field);
                }
            }
        }
        throw new IllegalArgumentException("missing " + filterType + "." + field);
    }

    private static JsonNode requiredFirst(JsonNode array, String label) {
        if (!array.isArray() || array.isEmpty()) {
            throw new IllegalArgumentException(label + " must contain one item");
        }
        return array.get(0);
    }

    private static String requiredText(JsonNode node, String field) {
        String value = node.path(field).asText("");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }

    private static void requireText(JsonNode node, String field, String expected) {
        String actual = requiredText(node, field);
        if (!expected.equals(actual)) {
            throw new IllegalArgumentException(
                    field + " expected=" + expected + " actual=" + actual);
        }
    }

    private static BigDecimal positiveDecimal(JsonNode node, String field) {
        BigDecimal value;
        try {
            value = new BigDecimal(requiredText(node, field));
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException(field + " is not a decimal", error);
        }
        if (value.signum() <= 0) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return value;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
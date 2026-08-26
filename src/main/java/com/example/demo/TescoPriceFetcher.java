package com.example.demo;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.ElementHandle;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.ServiceWorkerPolicy;
import com.microsoft.playwright.options.WaitUntilState;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.Paths;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Set;

@Component
public class TescoPriceFetcher {
    private static final Pattern TPNC_PATTERN = Pattern.compile("/products/(\\d+)");
    private static final Pattern MANGO_KEY_PATTERN = Pattern.compile("\"mangoApiKey\"\\s*:\\s*\"([^\"]+)\"");
    private static final String DEFAULT_API_URL = "https://xapi.tesco.com/";
    private String baseUrl = DEFAULT_API_URL;
    private static final String HOME_URL = "https://www.tesco.ie/groceries/en-IE/";
    private static final Set<String> BROWSER_ALLOWED_HOSTS = Set.of(
            "www.tesco.ie",
            "tesco.ie",
            "xapi.tesco.com",
            "digitalcontent.tesco.com",
            "digitalcontent.api.tesco.com",
            "secure.tesco.com",
            "login.tesco.com"
    );
    private static final Path CACHE_PATH =
            Paths.get(System.getProperty("user.home"), ".tesco-mango-key.properties");
    private static final long DEFAULT_TTL_MS = 24L * 60L * 60L * 1000L;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate;
    private final Logger logger = LoggerFactory.getLogger(TescoPriceFetcher.class);
    private String apiKey;

    public TescoPriceFetcher() {
        this.restTemplate = new RestTemplate();
    }

    TescoPriceFetcher(RestTemplate restTemplate, String apiKey, String baseUrl) {
        this.restTemplate = restTemplate;
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
    }

    public record TescoProductData(Float price, List<String> imageUrls) implements ProductData {
        public TescoProductData {
            imageUrls = imageUrls == null ? List.of() : List.copyOf(imageUrls);
        }
    }

    public Optional<Float> fetchPrice(String productUrl) {
        return fetchProductData(productUrl).flatMap(data -> Optional.ofNullable(data.price()));
    }

    public Optional<TescoProductData> fetchProductData(String productUrl) {
        if (!RetailerUrlPolicy.isAllowedRetailerUrl(productUrl, RetailerUrlPolicy.Retailer.TESCO)) {
            logger.debug("Rejected non-allowlisted Tesco URL");
            return Optional.empty();
        }
        try {
            var tpncOpt = extractTpnc(productUrl);
            if (tpncOpt.isEmpty()) {
                logger.debug("Tesco tpnc not found url={}", productUrl);
                return Optional.empty();
            }

            String tpnc = tpncOpt.get();
            String body = buildRequestBody(tpnc);

            var keyOpt = getApiKey(false);
            if (keyOpt.isEmpty()) {
                logger.debug("Tesco api key not available tpnc={}", tpnc);
                return Optional.empty();
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("region", "IE");
            headers.set("x-apikey", keyOpt.get());

            HttpEntity<String> entity = new HttpEntity<>(body, headers);
            try {
                return parseProductDataFromResponse(
                        restTemplate.postForObject(URI.create(baseUrl), entity, String.class),
                        tpnc,
                        productUrl
                );
            } catch (HttpClientErrorException.Forbidden e) {
                var mangoKey = getApiKey(true);
                if (mangoKey.isEmpty()) {
                    logger.debug("Tesco mangoApiKey not found after 403 tpnc={}", tpnc);
                    return Optional.empty();
                }

                HttpHeaders retryHeaders = new HttpHeaders();
                retryHeaders.setContentType(MediaType.APPLICATION_JSON);
                retryHeaders.set("region", "IE");
                retryHeaders.set("x-apikey", mangoKey.get());

                HttpEntity<String> retryEntity = new HttpEntity<>(body, retryHeaders);
                String retryResponse = restTemplate.postForObject(URI.create(baseUrl), retryEntity, String.class);
                return parseProductDataFromResponse(retryResponse, tpnc, productUrl);
            }
        } catch (Exception e) {
            logger.debug("Failed to fetch Tesco product data for url={}", productUrl, e);
            return Optional.empty();
        }
    }

    Optional<String> extractTpnc(String url) {
        if (url == null || url.isBlank()) {
            return Optional.empty();
        }
        var matcher = TPNC_PATTERN.matcher(url);
        if (matcher.find()) {
            return Optional.ofNullable(matcher.group(1));
        }
        return Optional.empty();
    }

    private String buildRequestBody(String tpnc) throws Exception {
        var payload = List.of(new GraphQlRequest(
                "GetProduct",
                new GraphQlVariables(tpnc, true),
                "query GetProduct($tpnc: String) { product(tpnc: $tpnc) { title price { actual unitPrice } media { defaultImage { url } } } }"
        ));
        return objectMapper.writeValueAsString(payload);
    }

    Optional<TescoProductData> parseProductDataFromResponse(String response, String tpnc, String productUrl) throws Exception {
        if (response == null || response.isBlank()) {
            logger.debug("Tesco empty response url={}", productUrl);
            return Optional.empty();
        }

        JsonNode root = objectMapper.readTree(response);
        var priceNode = root.at("/0/data/product/price/actual");
        Float price = null;
        if (!priceNode.isMissingNode() && !priceNode.isNull()) {
            price = (float) priceNode.asDouble();
        }

        var defaultImageUrl = root.at("/0/data/product/media/defaultImage/url").asText(null);
        var imageUrls = defaultImageUrl == null || defaultImageUrl.isBlank()
                ? List.<String>of()
                : List.of(defaultImageUrl);

        if (price == null && imageUrls.isEmpty()) {
            logger.debug("Tesco product data not found tpnc={}", tpnc);
            return Optional.empty();
        }

        return Optional.of(new TescoProductData(price, imageUrls));
    }

    Optional<String> getApiKey(boolean forceRefresh) {
        try {
            if (!forceRefresh && apiKey != null && !apiKey.isBlank()) {
                return Optional.of(apiKey);
            }

            if (!forceRefresh) {
                var cached = readCachedKey(getTtlMs());
                if (cached != null && !cached.isBlank()) {
                    apiKey = cached;
                    logger.debug("Using cached mangoApiKey.");
                    return Optional.of(cached);
                }
            }

            logger.debug(forceRefresh
                    ? "Force refresh enabled. Fetching fresh mangoApiKey."
                    : "Cache miss or stale. Fetching fresh mangoApiKey.");

            final String[] found = {null};
            Pattern pattern = MANGO_KEY_PATTERN;

            try (Playwright playwright = Playwright.create()) {
                Browser browser = playwright.firefox().launch(
                        new BrowserType.LaunchOptions().setHeadless(true)
                );

                BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                        .setUserAgent("Mozilla/5.0 (X11; Linux x86_64; rv:122.0) Gecko/20100101 Firefox/122.0")
                        .setLocale("en-IE")
                        .setServiceWorkers(ServiceWorkerPolicy.BLOCK)
                );
                Map<String, String> extraHeaders = new HashMap<>();
                extraHeaders.put("Accept-Language", "en-IE,en;q=0.9");
                extraHeaders.put("Upgrade-Insecure-Requests", "1");
                context.setExtraHTTPHeaders(extraHeaders);

                Page page = context.newPage();
                page.route("**/*", route -> {
                    if (!RetailerUrlPolicy.isAllowedBrowserUrl(route.request().url(), BROWSER_ALLOWED_HOSTS)) {
                        route.abort();
                        return;
                    }
                    var resourceType = route.request().resourceType();
                    if ("image".equals(resourceType) || "media".equals(resourceType)
                            || "font".equals(resourceType)) {
                        route.abort();
                        return;
                    }
                    route.resume();
                });

                page.onResponse(response -> {
                    if (found[0] != null) return;
                    String url = response.url();
                    String contentType = response.headers().getOrDefault("content-type", "");
                    boolean looksText = contentType.contains("text")
                            || contentType.contains("json")
                            || contentType.contains("javascript")
                            || url.contains(".js");

                    if (!looksText) return;

                    try {
                        byte[] body = response.body();
                        String text = new String(body, StandardCharsets.UTF_8);
                        Matcher m = pattern.matcher(text);
                        if (m.find()) {
                            found[0] = m.group(1);
                            logger.debug("Found mangoApiKey in response: {}", url);
                        }
                    } catch (Exception e) {
                        logger.debug("Response read failed: {} (status {}, type {})",
                                url, response.status(), contentType);
                    }
                });

                var nav = page.navigate(
                        HOME_URL,
                        new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                );
                if (nav != null) {
                    logger.debug("Tesco navigate status={} url={}", nav.status(), nav.url());
                }
                page.waitForLoadState(LoadState.NETWORKIDLE);

                String content = page.content();
                Matcher matcher = pattern.matcher(content);
                if (matcher.find()) {
                    var key = matcher.group(1);
                    writeCachedKey(key);
                    apiKey = key;
                    return Optional.ofNullable(key);
                }

                Object inlineKey = page.evaluate("() => {\n" +
                        "  const re = /\\\"mangoApiKey\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"/;\n" +
                        "  const scripts = Array.from(document.scripts).map(s => s.textContent || '');\n" +
                        "  for (const t of scripts) {\n" +
                        "    const m = re.exec(t);\n" +
                        "    if (m) return m[1];\n" +
                        "  }\n" +
                        "  return null;\n" +
                        "}");
                if (inlineKey != null) {
                    var key = inlineKey.toString();
                    writeCachedKey(key);
                    apiKey = key;
                    return Optional.of(key);
                }

                Object discoverKey = page.evaluate("() => {\n" +
                        "  const el = document.querySelector('script[type=\"application/discover+json\"]');\n" +
                        "  if (!el || !el.textContent) return null;\n" +
                        "  try {\n" +
                        "    const obj = JSON.parse(el.textContent);\n" +
                        "    const stack = [obj];\n" +
                        "    while (stack.length) {\n" +
                        "      const v = stack.pop();\n" +
                        "      if (v && typeof v === 'object') {\n" +
                        "        if (Object.prototype.hasOwnProperty.call(v, 'mangoApiKey')) return v.mangoApiKey;\n" +
                        "        for (const k in v) stack.push(v[k]);\n" +
                        "      }\n" +
                        "    }\n" +
                        "  } catch (e) {}\n" +
                        "  return null;\n" +
                        "}");
                if (discoverKey != null) {
                    var key = discoverKey.toString();
                    writeCachedKey(key);
                    apiKey = key;
                    return Optional.of(key);
                }

                if (found[0] != null) {
                    writeCachedKey(found[0]);
                    return Optional.of(found[0]);
                }

                URI base = URI.create(page.url());
                for (ElementHandle el : page.querySelectorAll("script[src]")) {
                    String src = el.getAttribute("src");
                    if (src == null || src.isBlank()) continue;
                    String fullUrl = base.resolve(src).toString();
                    if (!RetailerUrlPolicy.isAllowedBrowserUrl(fullUrl, BROWSER_ALLOWED_HOSTS)) {
                        continue;
                    }
                    try {
                        Object evalResult = page.evaluate(
                                "async (u) => { const r = await fetch(u); return await r.text(); }",
                                fullUrl
                        );
                        String text = evalResult == null ? "" : evalResult.toString();
                        Matcher m = pattern.matcher(text);
                        if (m.find()) {
                            found[0] = m.group(1);
                            logger.debug("Found mangoApiKey in script: {}", fullUrl);
                            break;
                        }
                    } catch (Exception e) {
                        logger.debug("Script fetch failed: {}", fullUrl);
                    }
                }

                if (found[0] != null) {
                    writeCachedKey(found[0]);
                    apiKey = found[0];
                    return Optional.of(found[0]);
                }
            }
        } catch (Exception e) {
            logger.debug("Failed to fetch Tesco mangoApiKey", e);
        }
        return Optional.empty();
    }

    private long getTtlMs() {
        String ttlEnv = System.getenv("MANGO_KEY_TTL_MS");
        if (ttlEnv != null && !ttlEnv.isBlank()) {
            try {
                return Long.parseLong(ttlEnv.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return DEFAULT_TTL_MS;
    }

    private String readCachedKey(long maxAgeMs) {
        if (!Files.exists(CACHE_PATH)) {
            return null;
        }
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(CACHE_PATH)) {
            props.load(in);
        } catch (Exception e) {
            logger.debug("Failed to read mango key cache: {}", e.getMessage());
            return null;
        }

        String key = props.getProperty("key", "");
        String fetchedAt = props.getProperty("fetchedAt", "");
        if (key.isBlank() || fetchedAt.isBlank()) {
            return null;
        }

        try {
            long ts = Long.parseLong(fetchedAt);
            long age = System.currentTimeMillis() - ts;
            if (maxAgeMs > 0 && age > maxAgeMs) {
                logger.debug("Cached mangoApiKey is stale (fetched at {}).", Instant.ofEpochMilli(ts));
                return null;
            }
        } catch (NumberFormatException ignored) {
            return null;
        }

        return key;
    }

    private void writeCachedKey(String key) {
        Properties props = new Properties();
        props.setProperty("key", key);
        props.setProperty("fetchedAt", Long.toString(System.currentTimeMillis()));
        try (OutputStream out = Files.newOutputStream(CACHE_PATH)) {
            props.store(out, "Tesco mangoApiKey cache");
            try {
                Files.setPosixFilePermissions(CACHE_PATH,
                        PosixFilePermissions.fromString("rw-------"));
            } catch (UnsupportedOperationException ignored) {
                // Non-POSIX filesystems still use the process umask.
            }
            logger.debug("Cached mangoApiKey to {}", CACHE_PATH);
        } catch (Exception e) {
            logger.debug("Failed to write mango key cache: {}", e.getMessage());
        }
    }

    private record GraphQlVariables(String tpnc, boolean skipReviews) {}
    private record GraphQlRequest(String operationName, GraphQlVariables variables, String query) {}
}

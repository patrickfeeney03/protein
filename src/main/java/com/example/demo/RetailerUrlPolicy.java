package com.example.demo;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Strict URL policy for retailer-backed lookups and browser navigation.
 *
 * <p>Do not replace this with a substring check. A URL such as
 * {@code https://aldi.ie.attacker.example/} must never be treated as an Aldi
 * URL.</p>
 */
public final class RetailerUrlPolicy {
    private static final Map<Retailer, Set<String>> RETAILER_HOSTS = Map.of(
            Retailer.ALDI, Set.of("aldi.ie", "www.aldi.ie", "groceries.aldi.ie"),
            Retailer.LIDL, Set.of("lidl.ie", "www.lidl.ie"),
            Retailer.DUNNES, Set.of("dunnesstoresgrocery.com", "www.dunnesstoresgrocery.com"),
            Retailer.TESCO, Set.of("tesco.ie", "www.tesco.ie")
    );

    private RetailerUrlPolicy() {
    }

    public enum Retailer {
        ALDI,
        LIDL,
        DUNNES,
        TESCO
    }

    /**
     * Returns the retailer only when the URL is an HTTPS URL with an exact
     * allowlisted hostname, no user info, and no non-standard port.
     */
    public static Optional<Retailer> retailerFor(String rawUrl) {
        var uri = parseHttpsUri(rawUrl).orElse(null);
        if (uri == null) {
            return Optional.empty();
        }

        var host = normalizedHost(uri);
        if (host == null) {
            return Optional.empty();
        }

        for (var entry : RETAILER_HOSTS.entrySet()) {
            if (entry.getValue().contains(host)) {
                return Optional.of(entry.getKey());
            }
        }
        return Optional.empty();
    }

    public static boolean isAllowedRetailerUrl(String rawUrl, Retailer retailer) {
        return retailer != null && retailerFor(rawUrl).filter(retailer::equals).isPresent();
    }

    /**
     * Resolves a configured hostname to a retailer without accepting URL
     * fragments, substrings, or arbitrary domains. This is used by scheduled
     * refresh jobs whose legacy configuration stores a host name as a string.
     */
    public static Optional<Retailer> retailerForHost(String rawHost) {
        if (rawHost == null || rawHost.isBlank()) {
            return Optional.empty();
        }
        var host = rawHost.trim().toLowerCase(Locale.ROOT);
        if (host.endsWith(".")) {
            return Optional.empty();
        }
        return RETAILER_HOSTS.entrySet().stream()
                .filter(entry -> entry.getValue().contains(host))
                .map(Map.Entry::getKey)
                .findFirst();
    }

    /**
     * Checks a browser request against an explicit host set. Browser resource
     * policy intentionally uses exact hosts; subdomains are not implicitly
     * trusted because DNS and redirects are part of the SSRF boundary.
     */
    public static boolean isAllowedBrowserUrl(String rawUrl, Set<String> allowedHosts) {
        var uri = parseHttpsUri(rawUrl).orElse(null);
        if (uri == null || allowedHosts == null || allowedHosts.isEmpty()) {
            return false;
        }
        var host = normalizedHost(uri);
        if (host == null) {
            return false;
        }
        return allowedHosts.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .anyMatch(host::equals);
    }

    public static Optional<URI> parseHttpsUri(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank() || rawUrl.length() > 2048) {
            return Optional.empty();
        }
        try {
            var uri = new URI(rawUrl.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || uri.getHost() == null
                    || uri.getUserInfo() != null
                    || (uri.getPort() != -1 && uri.getPort() != 443)
                    || uri.getPath() == null
                    || uri.getPath().isBlank()) {
                return Optional.empty();
            }
            return Optional.of(uri);
        } catch (URISyntaxException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static String normalizedHost(URI uri) {
        var host = uri.getHost();
        if (host == null || host.isBlank() || host.endsWith(".")) {
            return null;
        }
        return host.toLowerCase(Locale.ROOT);
    }
}

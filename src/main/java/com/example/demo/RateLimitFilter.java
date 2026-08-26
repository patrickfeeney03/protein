package com.example.demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;

public class RateLimitFilter extends OncePerRequestFilter {
    private static final String FOOD_POST_PATH = "/api/food";
    private static final String SCAN_IMAGE_PATH = "/api/food/scan-image";
    private static final String COMMENT_POST_PATH = "/api/comment";
    private static final String AUTH_POST_PATH = "/api/auth/logout";

    private static final long COUNTER_CLEANUP_INTERVAL = 64;
    private static final long CLEANUP_IDLE_THRESHOLD_MILLIS = Duration.ofMinutes(30).toMillis();

    private final RateLimitProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Map<String, SlidingWindowCounter> counters = new ConcurrentHashMap<>();
    private final Map<String, Semaphore> concurrencyGuards = new ConcurrentHashMap<>();
    private final Semaphore globalScanConcurrency;
    private final AtomicLong requestCount = new AtomicLong();

    public RateLimitFilter(RateLimitProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, Clock.systemUTC());
    }

    RateLimitFilter(RateLimitProperties properties, ObjectMapper objectMapper, Clock clock) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.globalScanConcurrency = new Semaphore(Math.max(1, properties.getScanImageGlobalMaxConcurrency()));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        var path = normalizePath(request.getServletPath());
        var method = request.getMethod();
        var rules = resolveRules(method, path);

        if (rules.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }

        var actorKey = resolveActorKey(request);
        var endpointKey = method + ":" + path;
        var nowMillis = clock.millis();

        Semaphore concurrencyGuard = null;
        var globalConcurrencyAcquired = false;
        if ("POST".equalsIgnoreCase(method) && SCAN_IMAGE_PATH.equals(path)
                && properties.getScanImageMaxConcurrency() > 0) {
            if (!globalScanConcurrency.tryAcquire()) {
                writeRateLimitResponse(response, Math.max(1,
                        properties.getScanImageCooldown().getWindow().toSeconds()));
                return;
            }
            globalConcurrencyAcquired = true;
            var guardKey = endpointKey + ":concurrency:" + actorKey;
            concurrencyGuard = concurrencyGuards.computeIfAbsent(
                    guardKey,
                    ignored -> new Semaphore(properties.getScanImageMaxConcurrency())
            );
            if (!concurrencyGuard.tryAcquire()) {
                globalScanConcurrency.release();
                writeRateLimitResponse(response, Math.max(1,
                        properties.getScanImageCooldown().getWindow().toSeconds()));
                return;
            }
        }

        try {
            for (var rule : rules) {
                var cacheKey = endpointKey + ":" + rule.ruleName() + ":" + actorKey;

                var counter = counters.computeIfAbsent(
                        cacheKey,
                        ignored -> new SlidingWindowCounter(rule.capacity(), rule.window())
                );

                var decision = counter.tryConsume(nowMillis);
                if (!decision.allowed()) {
                    writeRateLimitResponse(response, decision.retryAfterSeconds());
                    return;
                }
            }

            filterChain.doFilter(request, response);

            if (requestCount.incrementAndGet() % COUNTER_CLEANUP_INTERVAL == 0) {
                evictStaleCounters();
            }
        } finally {
            if (concurrencyGuard != null) {
                concurrencyGuard.release();
            }
            if (globalConcurrencyAcquired) {
                globalScanConcurrency.release();
            }
        }
    }

    void evictStaleCounters() {
        var cutoff = clock.millis() - CLEANUP_IDLE_THRESHOLD_MILLIS;
        counters.values().removeIf(c -> c.isIdleSince(cutoff));
        concurrencyGuards.entrySet().removeIf(entry -> entry.getValue().availablePermits()
                == properties.getScanImageMaxConcurrency());
    }

    private List<RuleConfig> resolveRules(String method, String path) {
        if (!"POST".equalsIgnoreCase(method)) {
            return List.of();
        }

        if (FOOD_POST_PATH.equals(path)) {
            return List.of(
                    toRuleConfig("food-create-cooldown", properties.getFoodCreateCooldown()),
                    toRuleConfig("food-create", properties.getFoodCreate())
            );
        }

        if (SCAN_IMAGE_PATH.equals(path)) {
            return List.of(
                    toRuleConfig("scan-image-cooldown", properties.getScanImageCooldown()),
                    toRuleConfig("scan-image", properties.getScanImage()),
                    toRuleConfig("scan-image-daily", properties.getScanImageDaily())
            );
        }

        if (COMMENT_POST_PATH.equals(path)) {
            return List.of(
                    toRuleConfig("comment-create-cooldown", properties.getCommentCreateCooldown()),
                    toRuleConfig("comment-create", properties.getCommentCreate())
            );
        }

        if (AUTH_POST_PATH.equals(path)) {
            return List.of(
                    toRuleConfig("auth-post-cooldown", properties.getAuthPostCooldown()),
                    toRuleConfig("auth-post", properties.getAuthPost())
            );
        }

        return List.of();
    }

    private RuleConfig toRuleConfig(String ruleName, RateLimitProperties.Rule rule) {
        return new RuleConfig(ruleName, rule.getCapacity(), rule.getWindow());
    }

    String resolveActorKey(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            var details = authentication.getDetails();
            if (details instanceof Long userId) {
                return "acct-id:" + userId;
            }

            var name = authentication.getName();
            if (name != null && !name.isBlank() && !"anonymousUser".equalsIgnoreCase(name)) {
                return "acct:" + name.trim().toLowerCase(Locale.ROOT);
            }
        }

        return "ip:" + extractClientIp(request);
    }

    private String extractClientIp(HttpServletRequest request) {
        var headerName = properties.getForwardedHeader();
        if (!headerName.isBlank() && isForwardedHeaderTrusted(request)) {
            var headerValue = request.getHeader(headerName);
            if (headerValue != null && !headerValue.isBlank()) {
                // X-Forwarded-For format: "client, proxy1, proxy2" — take leftmost
                var comma = headerValue.indexOf(',');
                var ip = comma >= 0 ? headerValue.substring(0, comma).trim() : headerValue.trim();
                if (!ip.isBlank()) {
                    return ip;
                }
            }
        }
        return request.getRemoteAddr();
    }

    private boolean isForwardedHeaderTrusted(HttpServletRequest request) {
        if (Boolean.TRUE.equals(request.getAttribute(OriginVerificationFilter.VERIFIED_ATTRIBUTE))) {
            return true;
        }
        if (!properties.isRequireTrustedProxy()) {
            return true;
        }
        var remoteAddr = request.getRemoteAddr();
        if (remoteAddr == null || remoteAddr.isBlank() || properties.getTrustedProxyCidrs() == null
                || properties.getTrustedProxyCidrs().isBlank()) {
            return false;
        }
        for (var configured : properties.getTrustedProxyCidrs().split(",")) {
            if (matchesCidr(remoteAddr.trim(), configured.trim())) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesCidr(String address, String cidr) {
        if (cidr == null || cidr.isBlank()) {
            return false;
        }
        try {
            var slash = cidr.indexOf('/');
            var networkText = slash >= 0 ? cidr.substring(0, slash).trim() : cidr.trim();
            var network = InetAddress.getByName(networkText);
            var candidate = InetAddress.getByName(address);
            if (network.getAddress().length != candidate.getAddress().length) {
                return false;
            }
            var prefixBits = slash >= 0
                    ? Integer.parseInt(cidr.substring(slash + 1).trim())
                    : network.getAddress().length * 8;
            if (prefixBits < 0 || prefixBits > network.getAddress().length * 8) {
                return false;
            }
            var fullBytes = prefixBits / 8;
            var remainingBits = prefixBits % 8;
            var networkBytes = network.getAddress();
            var candidateBytes = candidate.getAddress();
            for (var index = 0; index < fullBytes; index++) {
                if (networkBytes[index] != candidateBytes[index]) {
                    return false;
                }
            }
            if (remainingBits == 0) {
                return true;
            }
            var mask = (byte) (0xFF << (8 - remainingBits));
            return (networkBytes[fullBytes] & mask) == (candidateBytes[fullBytes] & mask);
        } catch (UnknownHostException | NumberFormatException e) {
            return false;
        }
    }

    String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }

        if (path.length() > 1 && path.endsWith("/")) {
            return path.substring(0, path.length() - 1);
        }

        return path;
    }

    private void writeRateLimitResponse(HttpServletResponse response, long retryAfterSeconds) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));

        var body = Map.of(
                "error", "rate_limit_exceeded",
                "message", "Too many requests. Try again later.",
                "retryAfterSeconds", retryAfterSeconds
        );
        objectMapper.writeValue(response.getWriter(), body);
    }

    private record RuleConfig(String ruleName, int capacity, Duration window) {
    }

    private record ConsumeDecision(boolean allowed, long retryAfterSeconds) {
    }

    private static class SlidingWindowCounter {
        private static final long MAX_RETRY_AFTER = Duration.ofHours(1).toSeconds();

        private final int capacity;
        private final long windowMillis;
        private final Deque<Long> requestTimes = new ArrayDeque<>();
        private volatile long lastAccessMillis = System.currentTimeMillis();
        private int consecutiveViolations = 0;

        private SlidingWindowCounter(int capacity, Duration window) {
            this.capacity = Math.max(1, capacity);
            this.windowMillis = Math.max(1, window.toMillis());
        }

        private synchronized ConsumeDecision tryConsume(long nowMillis) {
            lastAccessMillis = nowMillis;
            evictExpired(nowMillis);

            if (requestTimes.size() < capacity) {
                requestTimes.addLast(nowMillis);
                consecutiveViolations = 0;
                return new ConsumeDecision(true, 0);
            }

            var oldest = requestTimes.peekFirst();
            if (oldest == null) {
                requestTimes.addLast(nowMillis);
                consecutiveViolations = 0;
                return new ConsumeDecision(true, 0);
            }

            var millisUntilNext = (oldest + windowMillis) - nowMillis;
            var baseRetryAfterSeconds = Math.max(1, (millisUntilNext + 999) / 1000);
            consecutiveViolations++;
            var shift = Math.min(consecutiveViolations - 1, 31);
            var multiplier = 1L << shift;
            var retryAfterSeconds = Math.min(baseRetryAfterSeconds * multiplier, MAX_RETRY_AFTER);
            return new ConsumeDecision(false, retryAfterSeconds);
        }

        private void evictExpired(long nowMillis) {
            while (!requestTimes.isEmpty()) {
                var oldest = requestTimes.peekFirst();
                if (oldest == null || nowMillis - oldest >= windowMillis) {
                    requestTimes.removeFirst();
                } else {
                    break;
                }
            }
        }

        private boolean isIdleSince(long cutoffMillis) {
            return lastAccessMillis < cutoffMillis;
        }
    }
}

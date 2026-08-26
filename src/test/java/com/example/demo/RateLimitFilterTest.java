package com.example.demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RateLimitFilterTest {

    @Test
    void rateLimitUsesRemoteAddrByDefaultEvenWhenForwardedHeaderPresent() throws Exception {
        var properties = new RateLimitProperties();
        properties.setFoodCreate(new RateLimitProperties.Rule(1, Duration.ofHours(1)));

        var filter = new RateLimitFilter(properties, new ObjectMapper());
        var chain = mock(FilterChain.class);

        var firstRequest = new MockHttpServletRequest("POST", "/api/food");
        firstRequest.setServletPath("/api/food");
        firstRequest.setRemoteAddr("203.0.113.10");
        firstRequest.addHeader("X-Forwarded-For", "198.51.100.1");

        var firstResponse = new MockHttpServletResponse();
        filter.doFilter(firstRequest, firstResponse, chain);

        var secondRequest = new MockHttpServletRequest("POST", "/api/food");
        secondRequest.setServletPath("/api/food");
        secondRequest.setRemoteAddr("203.0.113.10");
        secondRequest.addHeader("X-Forwarded-For", "198.51.100.2");

        var secondResponse = new MockHttpServletResponse();
        filter.doFilter(secondRequest, secondResponse, chain);

        assertThat(firstResponse.getStatus()).isEqualTo(200);
        assertThat(secondResponse.getStatus()).isEqualTo(429);
        assertThat(secondResponse.getHeader("Retry-After")).isEqualTo("20");
        verify(chain).doFilter(firstRequest, firstResponse);
    }

    @Test
    void rateLimitUsesForwardedHeaderWhenConfigured() throws Exception {
        var properties = new RateLimitProperties();
        properties.setFoodCreate(new RateLimitProperties.Rule(1, Duration.ofHours(1)));
        properties.setForwardedHeader("X-Forwarded-For");

        var filter = new RateLimitFilter(properties, new ObjectMapper());
        var chain = mock(FilterChain.class);

        var firstRequest = new MockHttpServletRequest("POST", "/api/food");
        firstRequest.setServletPath("/api/food");
        firstRequest.setRemoteAddr("203.0.113.10");
        firstRequest.addHeader("X-Forwarded-For", "198.51.100.1");

        var firstResponse = new MockHttpServletResponse();
        filter.doFilter(firstRequest, firstResponse, chain);

        var secondRequest = new MockHttpServletRequest("POST", "/api/food");
        secondRequest.setServletPath("/api/food");
        secondRequest.setRemoteAddr("203.0.113.10");
        secondRequest.addHeader("X-Forwarded-For", "198.51.100.2");

        var secondResponse = new MockHttpServletResponse();
        filter.doFilter(secondRequest, secondResponse, chain);

        // Different X-Forwarded-For IPs → different buckets → both allowed
        assertThat(firstResponse.getStatus()).isEqualTo(200);
        assertThat(secondResponse.getStatus()).isEqualTo(200);
        verify(chain).doFilter(firstRequest, firstResponse);
        verify(chain).doFilter(secondRequest, secondResponse);
    }

    @Test
    void authPostRequestsAreRateLimited() throws Exception {
        var properties = new RateLimitProperties();
        properties.setAuthPost(new RateLimitProperties.Rule(1, Duration.ofMinutes(15)));
        properties.setAuthPostCooldown(new RateLimitProperties.Rule(1, Duration.ofSeconds(5)));

        var filter = new RateLimitFilter(properties, new ObjectMapper());
        var chain = mock(FilterChain.class);

        var firstRequest = new MockHttpServletRequest("POST", "/api/auth/logout");
        firstRequest.setServletPath("/api/auth/logout");
        firstRequest.setRemoteAddr("203.0.113.10");

        var firstResponse = new MockHttpServletResponse();
        filter.doFilter(firstRequest, firstResponse, chain);

        var secondRequest = new MockHttpServletRequest("POST", "/api/auth/logout");
        secondRequest.setServletPath("/api/auth/logout");
        secondRequest.setRemoteAddr("203.0.113.10");

        var secondResponse = new MockHttpServletResponse();
        filter.doFilter(secondRequest, secondResponse, chain);

        assertThat(firstResponse.getStatus()).isEqualTo(200);
        assertThat(secondResponse.getStatus()).isEqualTo(429);
        assertThat(secondResponse.getHeader("Retry-After")).isEqualTo("5");
    }

    @Test
    void scanImageRequestsAreRateLimited() throws Exception {
        var properties = new RateLimitProperties();
        properties.setScanImage(new RateLimitProperties.Rule(1, Duration.ofHours(1)));
        properties.setScanImageCooldown(new RateLimitProperties.Rule(1, Duration.ofSeconds(10)));

        var filter = new RateLimitFilter(properties, new ObjectMapper());
        var chain = mock(FilterChain.class);

        var firstRequest = new MockHttpServletRequest("POST", "/api/food/scan-image");
        firstRequest.setServletPath("/api/food/scan-image");
        firstRequest.setRemoteAddr("203.0.113.10");

        var firstResponse = new MockHttpServletResponse();
        filter.doFilter(firstRequest, firstResponse, chain);

        var secondRequest = new MockHttpServletRequest("POST", "/api/food/scan-image");
        secondRequest.setServletPath("/api/food/scan-image");
        secondRequest.setRemoteAddr("203.0.113.10");

        var secondResponse = new MockHttpServletResponse();
        filter.doFilter(secondRequest, secondResponse, chain);

        assertThat(firstResponse.getStatus()).isEqualTo(200);
        assertThat(secondResponse.getStatus()).isEqualTo(429);
        assertThat(secondResponse.getHeader("Retry-After")).isEqualTo("10");
    }

    @Test
    void scanImageRateLimitDoesNotAffectFoodCreate() throws Exception {
        var properties = new RateLimitProperties();
        properties.setScanImage(new RateLimitProperties.Rule(1, Duration.ofHours(1)));
        properties.setScanImageCooldown(new RateLimitProperties.Rule(1, Duration.ofSeconds(10)));

        var filter = new RateLimitFilter(properties, new ObjectMapper());
        var chain = mock(FilterChain.class);

        var scanRequest = new MockHttpServletRequest("POST", "/api/food/scan-image");
        scanRequest.setServletPath("/api/food/scan-image");
        scanRequest.setRemoteAddr("203.0.113.10");

        var scanResponse = new MockHttpServletResponse();
        filter.doFilter(scanRequest, scanResponse, chain);

        var foodRequest = new MockHttpServletRequest("POST", "/api/food");
        foodRequest.setServletPath("/api/food");
        foodRequest.setRemoteAddr("203.0.113.10");

        var foodResponse = new MockHttpServletResponse();
        filter.doFilter(foodRequest, foodResponse, chain);

        assertThat(scanResponse.getStatus()).isEqualTo(200);
        assertThat(foodResponse.getStatus()).isEqualTo(200);
    }

    @Test
    void scanImageExponentialBackoff_doublesRetryAfterOnConsecutiveViolations() throws Exception {
        var properties = new RateLimitProperties();
        properties.setScanImageCooldown(new RateLimitProperties.Rule(1, Duration.ofSeconds(10)));
        properties.setScanImage(new RateLimitProperties.Rule(10, Duration.ofHours(1)));

        var filter = new RateLimitFilter(properties, new ObjectMapper());
        var chain = mock(FilterChain.class);

        // 1st request — allowed
        var firstReq = new MockHttpServletRequest("POST", "/api/food/scan-image");
        firstReq.setServletPath("/api/food/scan-image");
        firstReq.setRemoteAddr("203.0.113.10");
        var firstRes = new MockHttpServletResponse();
        filter.doFilter(firstReq, firstRes, chain);
        assertThat(firstRes.getStatus()).isEqualTo(200);

        // 2nd request — denied, base retry-after (1×)
        var secondReq = new MockHttpServletRequest("POST", "/api/food/scan-image");
        secondReq.setServletPath("/api/food/scan-image");
        secondReq.setRemoteAddr("203.0.113.10");
        var secondRes = new MockHttpServletResponse();
        filter.doFilter(secondReq, secondRes, chain);
        assertThat(secondRes.getStatus()).isEqualTo(429);
        assertThat(secondRes.getHeader("Retry-After")).isEqualTo("10");

        // 3rd request — denied, doubled (2×)
        var thirdReq = new MockHttpServletRequest("POST", "/api/food/scan-image");
        thirdReq.setServletPath("/api/food/scan-image");
        thirdReq.setRemoteAddr("203.0.113.10");
        var thirdRes = new MockHttpServletResponse();
        filter.doFilter(thirdReq, thirdRes, chain);
        assertThat(thirdRes.getStatus()).isEqualTo(429);
        assertThat(thirdRes.getHeader("Retry-After")).isEqualTo("20");

        // 4th request — denied, quadrupled (4×)
        var fourthReq = new MockHttpServletRequest("POST", "/api/food/scan-image");
        fourthReq.setServletPath("/api/food/scan-image");
        fourthReq.setRemoteAddr("203.0.113.10");
        var fourthRes = new MockHttpServletResponse();
        filter.doFilter(fourthReq, fourthRes, chain);
        assertThat(fourthRes.getStatus()).isEqualTo(429);
        assertThat(fourthRes.getHeader("Retry-After")).isEqualTo("40");

        // 5th request — denied, octupled (8×)
        var fifthReq = new MockHttpServletRequest("POST", "/api/food/scan-image");
        fifthReq.setServletPath("/api/food/scan-image");
        fifthReq.setRemoteAddr("203.0.113.10");
        var fifthRes = new MockHttpServletResponse();
        filter.doFilter(fifthReq, fifthRes, chain);
        assertThat(fifthRes.getStatus()).isEqualTo(429);
        assertThat(fifthRes.getHeader("Retry-After")).isEqualTo("80");
    }

    @Test
    void scanImageExponentialBackoff_perKeyScoping() throws Exception {
        var properties = new RateLimitProperties();
        properties.setScanImageCooldown(new RateLimitProperties.Rule(1, Duration.ofSeconds(10)));
        properties.setScanImage(new RateLimitProperties.Rule(10, Duration.ofHours(1)));

        var filter = new RateLimitFilter(properties, new ObjectMapper());
        var chain = mock(FilterChain.class);

        // Actor A: 1st request allowed
        var reqA1 = new MockHttpServletRequest("POST", "/api/food/scan-image");
        reqA1.setServletPath("/api/food/scan-image");
        reqA1.setRemoteAddr("203.0.113.10");
        var resA1 = new MockHttpServletResponse();
        filter.doFilter(reqA1, resA1, chain);
        assertThat(resA1.getStatus()).isEqualTo(200);

        // Actor B: 1st request also allowed (different key → different counter)
        var reqB1 = new MockHttpServletRequest("POST", "/api/food/scan-image");
        reqB1.setServletPath("/api/food/scan-image");
        reqB1.setRemoteAddr("198.51.100.1");
        var resB1 = new MockHttpServletResponse();
        filter.doFilter(reqB1, resB1, chain);
        assertThat(resB1.getStatus()).isEqualTo(200);

        // Actor A: 2nd request — denied, base (1×)
        var reqA2 = new MockHttpServletRequest("POST", "/api/food/scan-image");
        reqA2.setServletPath("/api/food/scan-image");
        reqA2.setRemoteAddr("203.0.113.10");
        var resA2 = new MockHttpServletResponse();
        filter.doFilter(reqA2, resA2, chain);
        assertThat(resA2.getStatus()).isEqualTo(429);
        assertThat(resA2.getHeader("Retry-After")).isEqualTo("10");

        // Actor B: 2nd request — also denied, base (1×), independent counter
        var reqB2 = new MockHttpServletRequest("POST", "/api/food/scan-image");
        reqB2.setServletPath("/api/food/scan-image");
        reqB2.setRemoteAddr("198.51.100.1");
        var resB2 = new MockHttpServletResponse();
        filter.doFilter(reqB2, resB2, chain);
        assertThat(resB2.getStatus()).isEqualTo(429);
        assertThat(resB2.getHeader("Retry-After")).isEqualTo("10");

        // Actor A: 3rd request — denied, doubled (2×), proving violation accumulated
        var reqA3 = new MockHttpServletRequest("POST", "/api/food/scan-image");
        reqA3.setServletPath("/api/food/scan-image");
        reqA3.setRemoteAddr("203.0.113.10");
        var resA3 = new MockHttpServletResponse();
        filter.doFilter(reqA3, resA3, chain);
        assertThat(resA3.getStatus()).isEqualTo(429);
        assertThat(resA3.getHeader("Retry-After")).isEqualTo("20");

        // Actor B: 3rd request — also doubled (2×), independent accumulation
        var reqB3 = new MockHttpServletRequest("POST", "/api/food/scan-image");
        reqB3.setServletPath("/api/food/scan-image");
        reqB3.setRemoteAddr("198.51.100.1");
        var resB3 = new MockHttpServletResponse();
        filter.doFilter(reqB3, resB3, chain);
        assertThat(resB3.getStatus()).isEqualTo(429);
        assertThat(resB3.getHeader("Retry-After")).isEqualTo("20");
    }

    @Test
    void scanImageExponentialBackoff_capsAtMaxRetryAfter() throws Exception {
        var properties = new RateLimitProperties();
        properties.setScanImageCooldown(new RateLimitProperties.Rule(1, Duration.ofSeconds(10)));
        properties.setScanImage(new RateLimitProperties.Rule(10, Duration.ofHours(1)));

        var filter = new RateLimitFilter(properties, new ObjectMapper());
        var chain = mock(FilterChain.class);

        // Exhaust the cooldown capacity with 1 successful request
        var baseRequest = new MockHttpServletRequest("POST", "/api/food/scan-image");
        baseRequest.setServletPath("/api/food/scan-image");
        baseRequest.setRemoteAddr("203.0.113.10");
        var baseResponse = new MockHttpServletResponse();
        filter.doFilter(baseRequest, baseResponse, chain);
        assertThat(baseResponse.getStatus()).isEqualTo(200);

        // Send enough consecutive violations to hit the cap (3600s = 1 hour)
        // violations:  1   2   3   4   5   6   7   8   9   10
        // retry-after: 10  20  40  80  160 320 640 1280 2560 3600(capped)
        for (int i = 0; i < 9; i++) {
            var req = new MockHttpServletRequest("POST", "/api/food/scan-image");
            req.setServletPath("/api/food/scan-image");
            req.setRemoteAddr("203.0.113.10");
            var res = new MockHttpServletResponse();
            filter.doFilter(req, res, chain);
            assertThat(res.getStatus()).isEqualTo(429);
        }

        // 10th request should be capped at 3600
        var tenthReq = new MockHttpServletRequest("POST", "/api/food/scan-image");
        tenthReq.setServletPath("/api/food/scan-image");
        tenthReq.setRemoteAddr("203.0.113.10");
        var tenthRes = new MockHttpServletResponse();
        filter.doFilter(tenthReq, tenthRes, chain);
        assertThat(tenthRes.getStatus()).isEqualTo(429);
        assertThat(tenthRes.getHeader("Retry-After")).isEqualTo("3600");
    }

    @Test
    void foodCreateExponentialBackoff_doublesRetryAfterOnConsecutiveViolations() throws Exception {
        var properties = new RateLimitProperties();
        properties.setFoodCreateCooldown(new RateLimitProperties.Rule(1, Duration.ofSeconds(5)));
        properties.setFoodCreate(new RateLimitProperties.Rule(10, Duration.ofHours(1)));

        var filter = new RateLimitFilter(properties, new ObjectMapper());
        var chain = mock(FilterChain.class);

        // 1st request — allowed
        var firstReq = new MockHttpServletRequest("POST", "/api/food");
        firstReq.setServletPath("/api/food");
        firstReq.setRemoteAddr("203.0.113.10");
        var firstRes = new MockHttpServletResponse();
        filter.doFilter(firstReq, firstRes, chain);
        assertThat(firstRes.getStatus()).isEqualTo(200);

        // 2nd request — denied, base retry-after (1×)
        var secondReq = new MockHttpServletRequest("POST", "/api/food");
        secondReq.setServletPath("/api/food");
        secondReq.setRemoteAddr("203.0.113.10");
        var secondRes = new MockHttpServletResponse();
        filter.doFilter(secondReq, secondRes, chain);
        assertThat(secondRes.getStatus()).isEqualTo(429);
        assertThat(secondRes.getHeader("Retry-After")).isEqualTo("5");

        // 3rd request — denied, doubled (2×)
        var thirdReq = new MockHttpServletRequest("POST", "/api/food");
        thirdReq.setServletPath("/api/food");
        thirdReq.setRemoteAddr("203.0.113.10");
        var thirdRes = new MockHttpServletResponse();
        filter.doFilter(thirdReq, thirdRes, chain);
        assertThat(thirdRes.getStatus()).isEqualTo(429);
        assertThat(thirdRes.getHeader("Retry-After")).isEqualTo("10");

        // 4th request — denied, quadrupled (4×)
        var fourthReq = new MockHttpServletRequest("POST", "/api/food");
        fourthReq.setServletPath("/api/food");
        fourthReq.setRemoteAddr("203.0.113.10");
        var fourthRes = new MockHttpServletResponse();
        filter.doFilter(fourthReq, fourthRes, chain);
        assertThat(fourthRes.getStatus()).isEqualTo(429);
        assertThat(fourthRes.getHeader("Retry-After")).isEqualTo("20");

        // 5th request — denied, octupled (8×)
        var fifthReq = new MockHttpServletRequest("POST", "/api/food");
        fifthReq.setServletPath("/api/food");
        fifthReq.setRemoteAddr("203.0.113.10");
        var fifthRes = new MockHttpServletResponse();
        filter.doFilter(fifthReq, fifthRes, chain);
        assertThat(fifthRes.getStatus()).isEqualTo(429);
        assertThat(fifthRes.getHeader("Retry-After")).isEqualTo("40");
    }

    @Test
    void commentCreateExponentialBackoff_doublesRetryAfterOnConsecutiveViolations() throws Exception {
        var properties = new RateLimitProperties();
        properties.setCommentCreateCooldown(new RateLimitProperties.Rule(1, Duration.ofSeconds(4)));
        properties.setCommentCreate(new RateLimitProperties.Rule(10, Duration.ofHours(1)));

        var filter = new RateLimitFilter(properties, new ObjectMapper());
        var chain = mock(FilterChain.class);

        // 1st request — allowed
        var firstReq = new MockHttpServletRequest("POST", "/api/comment");
        firstReq.setServletPath("/api/comment");
        firstReq.setRemoteAddr("203.0.113.10");
        var firstRes = new MockHttpServletResponse();
        filter.doFilter(firstReq, firstRes, chain);
        assertThat(firstRes.getStatus()).isEqualTo(200);

        // 2nd request — denied, base retry-after (1×)
        var secondReq = new MockHttpServletRequest("POST", "/api/comment");
        secondReq.setServletPath("/api/comment");
        secondReq.setRemoteAddr("203.0.113.10");
        var secondRes = new MockHttpServletResponse();
        filter.doFilter(secondReq, secondRes, chain);
        assertThat(secondRes.getStatus()).isEqualTo(429);
        assertThat(secondRes.getHeader("Retry-After")).isEqualTo("4");

        // 3rd request — denied, doubled (2×)
        var thirdReq = new MockHttpServletRequest("POST", "/api/comment");
        thirdReq.setServletPath("/api/comment");
        thirdReq.setRemoteAddr("203.0.113.10");
        var thirdRes = new MockHttpServletResponse();
        filter.doFilter(thirdReq, thirdRes, chain);
        assertThat(thirdRes.getStatus()).isEqualTo(429);
        assertThat(thirdRes.getHeader("Retry-After")).isEqualTo("8");

        // 4th request — denied, quadrupled (4×)
        var fourthReq = new MockHttpServletRequest("POST", "/api/comment");
        fourthReq.setServletPath("/api/comment");
        fourthReq.setRemoteAddr("203.0.113.10");
        var fourthRes = new MockHttpServletResponse();
        filter.doFilter(fourthReq, fourthRes, chain);
        assertThat(fourthRes.getStatus()).isEqualTo(429);
        assertThat(fourthRes.getHeader("Retry-After")).isEqualTo("16");

        // 5th request — denied, octupled (8×)
        var fifthReq = new MockHttpServletRequest("POST", "/api/comment");
        fifthReq.setServletPath("/api/comment");
        fifthReq.setRemoteAddr("203.0.113.10");
        var fifthRes = new MockHttpServletResponse();
        filter.doFilter(fifthReq, fifthRes, chain);
        assertThat(fifthRes.getStatus()).isEqualTo(429);
        assertThat(fifthRes.getHeader("Retry-After")).isEqualTo("32");
    }

    @Test
    void authPostExponentialBackoff_doublesRetryAfterOnConsecutiveViolations() throws Exception {
        var properties = new RateLimitProperties();
        properties.setAuthPostCooldown(new RateLimitProperties.Rule(1, Duration.ofSeconds(5)));
        properties.setAuthPost(new RateLimitProperties.Rule(20, Duration.ofMinutes(15)));

        var filter = new RateLimitFilter(properties, new ObjectMapper());
        var chain = mock(FilterChain.class);

        var firstReq = new MockHttpServletRequest("POST", "/api/auth/logout");
        firstReq.setServletPath("/api/auth/logout");
        firstReq.setRemoteAddr("203.0.113.10");
        var firstRes = new MockHttpServletResponse();
        filter.doFilter(firstReq, firstRes, chain);
        assertThat(firstRes.getStatus()).isEqualTo(200);

        var secondReq = new MockHttpServletRequest("POST", "/api/auth/logout");
        secondReq.setServletPath("/api/auth/logout");
        secondReq.setRemoteAddr("203.0.113.10");
        var secondRes = new MockHttpServletResponse();
        filter.doFilter(secondReq, secondRes, chain);
        assertThat(secondRes.getStatus()).isEqualTo(429);
        assertThat(secondRes.getHeader("Retry-After")).isEqualTo("5");

        var thirdReq = new MockHttpServletRequest("POST", "/api/auth/logout");
        thirdReq.setServletPath("/api/auth/logout");
        thirdReq.setRemoteAddr("203.0.113.10");
        var thirdRes = new MockHttpServletResponse();
        filter.doFilter(thirdReq, thirdRes, chain);
        assertThat(thirdRes.getStatus()).isEqualTo(429);
        assertThat(thirdRes.getHeader("Retry-After")).isEqualTo("10");

        var fourthReq = new MockHttpServletRequest("POST", "/api/auth/logout");
        fourthReq.setServletPath("/api/auth/logout");
        fourthReq.setRemoteAddr("203.0.113.10");
        var fourthRes = new MockHttpServletResponse();
        filter.doFilter(fourthReq, fourthRes, chain);
        assertThat(fourthRes.getStatus()).isEqualTo(429);
        assertThat(fourthRes.getHeader("Retry-After")).isEqualTo("20");

        var fifthReq = new MockHttpServletRequest("POST", "/api/auth/logout");
        fifthReq.setServletPath("/api/auth/logout");
        fifthReq.setRemoteAddr("203.0.113.10");
        var fifthRes = new MockHttpServletResponse();
        filter.doFilter(fifthReq, fifthRes, chain);
        assertThat(fifthRes.getStatus()).isEqualTo(429);
        assertThat(fifthRes.getHeader("Retry-After")).isEqualTo("40");
    }

    @Test
    void foodCreateExponentialBackoff_capsAtMaxRetryAfter() throws Exception {
        var properties = new RateLimitProperties();
        properties.setFoodCreateCooldown(new RateLimitProperties.Rule(1, Duration.ofSeconds(5)));
        properties.setFoodCreate(new RateLimitProperties.Rule(10, Duration.ofHours(1)));

        var filter = new RateLimitFilter(properties, new ObjectMapper());
        var chain = mock(FilterChain.class);

        // Exhaust the cooldown capacity with 1 successful request
        var baseRequest = new MockHttpServletRequest("POST", "/api/food");
        baseRequest.setServletPath("/api/food");
        baseRequest.setRemoteAddr("203.0.113.10");
        var baseResponse = new MockHttpServletResponse();
        filter.doFilter(baseRequest, baseResponse, chain);
        assertThat(baseResponse.getStatus()).isEqualTo(200);

        // Send enough consecutive violations to hit the cap (3600s = 1 hour)
        // 5s base, so 11 violations needed (2^10 * 5 = 5120 → capped at 3600)
        // violations:   1   2   3   4   5   6   7   8   9   10   11
        // retry-after:  5   10  20  40  80  160 320 640 1280 2560 3600(capped)
        for (int i = 0; i < 10; i++) {
            var req = new MockHttpServletRequest("POST", "/api/food");
            req.setServletPath("/api/food");
            req.setRemoteAddr("203.0.113.10");
            var res = new MockHttpServletResponse();
            filter.doFilter(req, res, chain);
            assertThat(res.getStatus()).isEqualTo(429);
        }

        // 11th request should be capped at 3600
        var eleventhReq = new MockHttpServletRequest("POST", "/api/food");
        eleventhReq.setServletPath("/api/food");
        eleventhReq.setRemoteAddr("203.0.113.10");
        var eleventhRes = new MockHttpServletResponse();
        filter.doFilter(eleventhReq, eleventhRes, chain);
        assertThat(eleventhRes.getStatus()).isEqualTo(429);
        assertThat(eleventhRes.getHeader("Retry-After")).isEqualTo("3600");
    }

    @Test
    void commentCreateExponentialBackoff_capsAtMaxRetryAfter() throws Exception {
        var properties = new RateLimitProperties();
        properties.setCommentCreateCooldown(new RateLimitProperties.Rule(1, Duration.ofSeconds(4)));
        properties.setCommentCreate(new RateLimitProperties.Rule(10, Duration.ofHours(1)));

        var filter = new RateLimitFilter(properties, new ObjectMapper());
        var chain = mock(FilterChain.class);

        // Exhaust the cooldown capacity with 1 successful request
        var baseRequest = new MockHttpServletRequest("POST", "/api/comment");
        baseRequest.setServletPath("/api/comment");
        baseRequest.setRemoteAddr("203.0.113.10");
        var baseResponse = new MockHttpServletResponse();
        filter.doFilter(baseRequest, baseResponse, chain);
        assertThat(baseResponse.getStatus()).isEqualTo(200);

        // Send enough consecutive violations to hit the cap (3600s = 1 hour)
        // 4s base, so 11 violations needed (2^10 * 4 = 4096 → capped at 3600)
        // violations:   1   2   3   4    5    6    7    8    9    10   11
        // retry-after:  4   8   16  32   64   128  256  512  1024 2048 3600(capped)
        for (int i = 0; i < 10; i++) {
            var req = new MockHttpServletRequest("POST", "/api/comment");
            req.setServletPath("/api/comment");
            req.setRemoteAddr("203.0.113.10");
            var res = new MockHttpServletResponse();
            filter.doFilter(req, res, chain);
            assertThat(res.getStatus()).isEqualTo(429);
        }

        // 11th request should be capped at 3600
        var eleventhReq = new MockHttpServletRequest("POST", "/api/comment");
        eleventhReq.setServletPath("/api/comment");
        eleventhReq.setRemoteAddr("203.0.113.10");
        var eleventhRes = new MockHttpServletResponse();
        filter.doFilter(eleventhReq, eleventhRes, chain);
        assertThat(eleventhRes.getStatus()).isEqualTo(429);
        assertThat(eleventhRes.getHeader("Retry-After")).isEqualTo("3600");
    }

    @Test
    void authPostExponentialBackoff_capsAtMaxRetryAfter() throws Exception {
        var properties = new RateLimitProperties();
        properties.setAuthPostCooldown(new RateLimitProperties.Rule(1, Duration.ofSeconds(5)));
        properties.setAuthPost(new RateLimitProperties.Rule(20, Duration.ofMinutes(15)));

        var filter = new RateLimitFilter(properties, new ObjectMapper());
        var chain = mock(FilterChain.class);

        // Exhaust the cooldown capacity with 1 successful request
        var baseRequest = new MockHttpServletRequest("POST", "/api/auth/logout");
        baseRequest.setServletPath("/api/auth/logout");
        baseRequest.setRemoteAddr("203.0.113.10");
        var baseResponse = new MockHttpServletResponse();
        filter.doFilter(baseRequest, baseResponse, chain);
        assertThat(baseResponse.getStatus()).isEqualTo(200);

        // Send enough consecutive violations to hit the cap (3600s = 1 hour)
        // 5s base, so 11 violations needed (2^10 * 5 = 5120 → capped at 3600)
        // violations:   1   2   3   4   5   6   7   8   9   10   11
        // retry-after:  5   10  20  40  80  160 320 640 1280 2560 3600(capped)
        for (int i = 0; i < 10; i++) {
            var req = new MockHttpServletRequest("POST", "/api/auth/logout");
            req.setServletPath("/api/auth/logout");
            req.setRemoteAddr("203.0.113.10");
            var res = new MockHttpServletResponse();
            filter.doFilter(req, res, chain);
            assertThat(res.getStatus()).isEqualTo(429);
        }

        // 11th request should be capped at 3600
        var eleventhReq = new MockHttpServletRequest("POST", "/api/auth/logout");
        eleventhReq.setServletPath("/api/auth/logout");
        eleventhReq.setRemoteAddr("203.0.113.10");
        var eleventhRes = new MockHttpServletResponse();
        filter.doFilter(eleventhReq, eleventhRes, chain);
        assertThat(eleventhRes.getStatus()).isEqualTo(429);
        assertThat(eleventhRes.getHeader("Retry-After")).isEqualTo("3600");
    }

    @Test
    void rateLimitResponse_hasCorrectJsonBody() throws Exception {
        var properties = new RateLimitProperties();
        properties.setFoodCreateCooldown(new RateLimitProperties.Rule(1, Duration.ofSeconds(10)));
        properties.setFoodCreate(new RateLimitProperties.Rule(10, Duration.ofHours(1)));

        var filter = new RateLimitFilter(properties, new ObjectMapper());
        var chain = mock(FilterChain.class);

        var req1 = new MockHttpServletRequest("POST", "/api/food");
        req1.setServletPath("/api/food");
        req1.setRemoteAddr("203.0.113.10");
        var res1 = new MockHttpServletResponse();
        filter.doFilter(req1, res1, chain);

        var req2 = new MockHttpServletRequest("POST", "/api/food");
        req2.setServletPath("/api/food");
        req2.setRemoteAddr("203.0.113.10");
        var res2 = new MockHttpServletResponse();
        filter.doFilter(req2, res2, chain);

        assertThat(res2.getStatus()).isEqualTo(429);
        assertThat(res2.getContentType()).isEqualTo("application/json");

        var mapper = new ObjectMapper();
        var body = mapper.readValue(res2.getContentAsString(), Map.class);
        assertThat(body)
                .containsEntry("error", "rate_limit_exceeded")
                .containsEntry("message", "Too many requests. Try again later.")
                .containsEntry("retryAfterSeconds", 10);
    }

    @Test
    void rateLimitResponse_retryAfterSecondsMatchesHeaderOnBackoff() throws Exception {
        var properties = new RateLimitProperties();
        properties.setScanImageCooldown(new RateLimitProperties.Rule(1, Duration.ofSeconds(10)));
        properties.setScanImage(new RateLimitProperties.Rule(10, Duration.ofHours(1)));

        var filter = new RateLimitFilter(properties, new ObjectMapper());
        var chain = mock(FilterChain.class);

        // 1st request — allowed
        var req1 = new MockHttpServletRequest("POST", "/api/food/scan-image");
        req1.setServletPath("/api/food/scan-image");
        req1.setRemoteAddr("203.0.113.10");
        var res1 = new MockHttpServletResponse();
        filter.doFilter(req1, res1, chain);
        assertThat(res1.getStatus()).isEqualTo(200);

        // 2nd request — Retry-After = 10 (base), body.retryAfterSeconds = 10
        var req2 = new MockHttpServletRequest("POST", "/api/food/scan-image");
        req2.setServletPath("/api/food/scan-image");
        req2.setRemoteAddr("203.0.113.10");
        var res2 = new MockHttpServletResponse();
        filter.doFilter(req2, res2, chain);

        assertThat(res2.getStatus()).isEqualTo(429);
        var mapper = new ObjectMapper();
        var body2 = mapper.readValue(res2.getContentAsString(), Map.class);
        assertThat(body2.get("retryAfterSeconds"))
                .isEqualTo(Integer.valueOf(res2.getHeader("Retry-After")));

        // 3rd request — Retry-After = 20 (doubled), body.retryAfterSeconds = 20
        var req3 = new MockHttpServletRequest("POST", "/api/food/scan-image");
        req3.setServletPath("/api/food/scan-image");
        req3.setRemoteAddr("203.0.113.10");
        var res3 = new MockHttpServletResponse();
        filter.doFilter(req3, res3, chain);

        assertThat(res3.getStatus()).isEqualTo(429);
        var body3 = mapper.readValue(res3.getContentAsString(), Map.class);
        assertThat(body3.get("retryAfterSeconds"))
                .isEqualTo(Integer.valueOf(res3.getHeader("Retry-After")));
    }

    // --- normalizePath() ---

    @Test
    void normalizePath_null_returnsEmpty() {
        var filter = new RateLimitFilter(new RateLimitProperties(), new ObjectMapper());
        assertThat(filter.normalizePath(null)).isEmpty();
    }

    @Test
    void normalizePath_empty_returnsEmpty() {
        var filter = new RateLimitFilter(new RateLimitProperties(), new ObjectMapper());
        assertThat(filter.normalizePath("")).isEmpty();
    }

    @Test
    void normalizePath_blank_returnsEmpty() {
        var filter = new RateLimitFilter(new RateLimitProperties(), new ObjectMapper());
        assertThat(filter.normalizePath("  ")).isEmpty();
    }

    @Test
    void normalizePath_rootSlash_preserved() {
        var filter = new RateLimitFilter(new RateLimitProperties(), new ObjectMapper());
        assertThat(filter.normalizePath("/")).isEqualTo("/");
    }

    @Test
    void normalizePath_noTrailingSlash_unchanged() {
        var filter = new RateLimitFilter(new RateLimitProperties(), new ObjectMapper());
        assertThat(filter.normalizePath("/api/food")).isEqualTo("/api/food");
    }

    @Test
    void normalizePath_trailingSlash_stripped() {
        var filter = new RateLimitFilter(new RateLimitProperties(), new ObjectMapper());
        assertThat(filter.normalizePath("/api/food/")).isEqualTo("/api/food");
    }

    @Test
    void normalizePath_shortPathWithSlash_stripped() {
        var filter = new RateLimitFilter(new RateLimitProperties(), new ObjectMapper());
        assertThat(filter.normalizePath("/a/")).isEqualTo("/a");
    }

    @Test
    void normalizePath_shortPathNoSlash_unchanged() {
        var filter = new RateLimitFilter(new RateLimitProperties(), new ObjectMapper());
        assertThat(filter.normalizePath("/a")).isEqualTo("/a");
    }

    @Test
    void normalizePath_doubleTrailingSlash_stripsOnlyOne() {
        var filter = new RateLimitFilter(new RateLimitProperties(), new ObjectMapper());
        assertThat(filter.normalizePath("/api/food//")).isEqualTo("/api/food/");
    }

    @Test
    void normalizePath_noLeadingSlash_unchanged() {
        var filter = new RateLimitFilter(new RateLimitProperties(), new ObjectMapper());
        assertThat(filter.normalizePath("api/food")).isEqualTo("api/food");
    }

    // --- resolveActorKey() ---

    @Test
    void resolveActorKey_noAuth_returnsIpWithRemoteAddr() {
        SecurityContextHolder.clearContext();
        var filter = new RateLimitFilter(new RateLimitProperties(), new ObjectMapper());
        var request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.10");
        try {
            assertThat(filter.resolveActorKey(request)).isEqualTo("ip:203.0.113.10");
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void resolveActorKey_authenticatedWithLongDetails_returnsAcctId() {
        var auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getDetails()).thenReturn(42L);
        SecurityContextHolder.getContext().setAuthentication(auth);
        var filter = new RateLimitFilter(new RateLimitProperties(), new ObjectMapper());
        try {
            assertThat(filter.resolveActorKey(new MockHttpServletRequest())).isEqualTo("acct-id:42");
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void resolveActorKey_authenticatedWithName_returnsAcctName() {
        var auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getDetails()).thenReturn("non-long-details");
        when(auth.getName()).thenReturn("TestUser");
        SecurityContextHolder.getContext().setAuthentication(auth);
        var filter = new RateLimitFilter(new RateLimitProperties(), new ObjectMapper());
        try {
            assertThat(filter.resolveActorKey(new MockHttpServletRequest())).isEqualTo("acct:testuser");
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void resolveActorKey_authenticatedAsAnonymousUser_returnsIp() {
        var auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getDetails()).thenReturn("non-long-details");
        when(auth.getName()).thenReturn("anonymousUser");
        SecurityContextHolder.getContext().setAuthentication(auth);
        var filter = new RateLimitFilter(new RateLimitProperties(), new ObjectMapper());
        var request = new MockHttpServletRequest();
        request.setRemoteAddr("198.51.100.1");
        try {
            assertThat(filter.resolveActorKey(request)).isEqualTo("ip:198.51.100.1");
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void resolveActorKey_authenticatedWithNullName_returnsIp() {
        var auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getDetails()).thenReturn("non-long-details");
        when(auth.getName()).thenReturn(null);
        SecurityContextHolder.getContext().setAuthentication(auth);
        var filter = new RateLimitFilter(new RateLimitProperties(), new ObjectMapper());
        var request = new MockHttpServletRequest();
        request.setRemoteAddr("198.51.100.1");
        try {
            assertThat(filter.resolveActorKey(request)).isEqualTo("ip:198.51.100.1");
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    // --- evictStaleCounters() ---

    private static MockHttpServletRequest request(String method, String path, String remoteAddr) {
        var req = new MockHttpServletRequest(method, path);
        req.setServletPath(path);
        req.setRemoteAddr(remoteAddr);
        return req;
    }

    @Test
    void evictStaleCounters_removesIdleCounters() throws Exception {
        var clock = mock(Clock.class);
        var time = new AtomicLong(0L);
        when(clock.millis()).thenAnswer(invocation -> time.get());

        var properties = new RateLimitProperties();
        properties.setFoodCreateCooldown(new RateLimitProperties.Rule(200, Duration.ofHours(1)));
        properties.setFoodCreate(new RateLimitProperties.Rule(1, Duration.ofHours(1)));

        var filter = new RateLimitFilter(properties, new ObjectMapper(), clock);
        var chain = mock(FilterChain.class);

        // time=0: IP A request → allowed (creates counter with lastAccessMillis=0)
        filter.doFilter(request("POST", "/api/food", "203.0.113.10"), new MockHttpServletResponse(), chain);

        // time=0: IP B request → allowed (creates counter with lastAccessMillis=0)
        filter.doFilter(request("POST", "/api/food", "198.51.100.1"), new MockHttpServletResponse(), chain);

        // time=0: IP B again → denied (capacity=1 exhausted for IP B)
        var resB2 = new MockHttpServletResponse();
        filter.doFilter(request("POST", "/api/food", "198.51.100.1"), resB2, chain);
        assertThat(resB2.getStatus()).isEqualTo(429);

        // Advance clock past the 30-minute idle threshold
        time.set(1_860_000L);

        // Evict stale counters
        filter.evictStaleCounters();

        // Both counters had lastAccessMillis=0, cutoff=60,000, both idle → evicted
        // IP B's counter was evicted → new request should be allowed (fresh counter)
        var resB3 = new MockHttpServletResponse();
        filter.doFilter(request("POST", "/api/food", "198.51.100.1"), resB3, chain);
        assertThat(resB3.getStatus()).isEqualTo(200);

        // IP A's counter was evicted → new request should also be allowed (fresh counter)
        var resA2 = new MockHttpServletResponse();
        filter.doFilter(request("POST", "/api/food", "203.0.113.10"), resA2, chain);
        assertThat(resA2.getStatus()).isEqualTo(200);
    }

    @Test
    void evictStaleCounters_preservesRecentCounters() throws Exception {
        var clock = mock(Clock.class);
        var time = new AtomicLong(0L);
        when(clock.millis()).thenAnswer(invocation -> time.get());

        var properties = new RateLimitProperties();
        properties.setFoodCreateCooldown(new RateLimitProperties.Rule(200, Duration.ofHours(1)));
        properties.setFoodCreate(new RateLimitProperties.Rule(1, Duration.ofHours(1)));

        var filter = new RateLimitFilter(properties, new ObjectMapper(), clock);
        var chain = mock(FilterChain.class);

        // IP A at time=0 → allowed
        filter.doFilter(request("POST", "/api/food", "203.0.113.10"), new MockHttpServletResponse(), chain);

        // Advance 5 minutes (within 30-min idle threshold)
        time.set(300_000L);

        // IP B at time=5min → allowed
        filter.doFilter(request("POST", "/api/food", "198.51.100.1"), new MockHttpServletResponse(), chain);

        // Advance 5 more minutes (total 10 min, still within threshold)
        time.set(600_000L);

        // Evict stale counters (cutoff = 600s - 1800s = -1200s from epoch)
        // All lastAccessMillis >= 0 > -1200s → none idle → none evicted
        filter.evictStaleCounters();

        // Both counters should still be present → both should be rate-limited
        var resA2 = new MockHttpServletResponse();
        filter.doFilter(request("POST", "/api/food", "203.0.113.10"), resA2, chain);
        assertThat(resA2.getStatus()).isEqualTo(429);

        var resB2 = new MockHttpServletResponse();
        filter.doFilter(request("POST", "/api/food", "198.51.100.1"), resB2, chain);
        assertThat(resB2.getStatus()).isEqualTo(429);
    }

    @Test
    void evictStaleCounters_removesOnlyIdleCounters() throws Exception {
        var clock = mock(Clock.class);
        var time = new AtomicLong(0L);
        when(clock.millis()).thenAnswer(invocation -> time.get());

        var properties = new RateLimitProperties();
        properties.setFoodCreateCooldown(new RateLimitProperties.Rule(200, Duration.ofHours(1)));
        properties.setFoodCreate(new RateLimitProperties.Rule(1, Duration.ofHours(1)));

        var filter = new RateLimitFilter(properties, new ObjectMapper(), clock);
        var chain = mock(FilterChain.class);

        // IP A at time=0 → allowed (lastAccessMillis=0)
        filter.doFilter(request("POST", "/api/food", "203.0.113.10"), new MockHttpServletResponse(), chain);

        // Advance ~6 min 40s — IP A's counter now 400s old, keeps IP B recent
        time.set(400_000L);

        // IP B at time=400s → allowed (lastAccessMillis=400,000)
        filter.doFilter(request("POST", "/api/food", "198.51.100.1"), new MockHttpServletResponse(), chain);

        // Advance to 36 min total (2,160,000ms)
        // cutoff = 2,160,000 - 1,800,000 = 360,000
        // IP A: lastAccessMillis=0 < 360,000 → IDLE → evicted
        // IP B: lastAccessMillis=400,000 >= 360,000 → NOT idle → preserved
        time.set(2_160_000L);

        filter.evictStaleCounters();

        // IP B's counter preserved → still rate-limited
        var resB2 = new MockHttpServletResponse();
        filter.doFilter(request("POST", "/api/food", "198.51.100.1"), resB2, chain);
        assertThat(resB2.getStatus()).isEqualTo(429);

        // IP A's counter evicted → new request allowed (fresh counter)
        var resA2 = new MockHttpServletResponse();
        filter.doFilter(request("POST", "/api/food", "203.0.113.10"), resA2, chain);
        assertThat(resA2.getStatus()).isEqualTo(200);
    }

    @Test
    void evictStaleCounters_emptyCounters_doesNotThrow() {
        var filter = new RateLimitFilter(new RateLimitProperties(), new ObjectMapper(), Clock.systemUTC());
        filter.evictStaleCounters();
    }
}

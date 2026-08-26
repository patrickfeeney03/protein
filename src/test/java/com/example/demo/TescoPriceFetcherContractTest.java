package com.example.demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.List;
import java.util.Optional;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.jupiter.api.Assertions.*;

class TescoPriceFetcherContractTest {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(options().dynamicPort())
            .build();

    private final ObjectMapper objectMapper = new ObjectMapper();
    private TescoPriceFetcher fetcher;
    private String baseUrl;

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + wireMock.getPort() + "/";
        var restTemplate = new org.springframework.web.client.RestTemplate();
        fetcher = new TescoPriceFetcher(restTemplate, "test-api-key", baseUrl);
    }

    private static final String TEST_URL = "https://www.tesco.ie/groceries/en-IE/products/317328050";
    private static final String MINIMAL_RESPONSE = """
            [{"data":{"product":{"title":"Test","price":{"actual":1.0}}}}]
            """;

    @Nested
    class RequestContract {

        @Test
        void sendsPostToBaseUrl() {
            wireMock.stubFor(post(urlPathEqualTo("/"))
                    .willReturn(aResponse().withStatus(200).withBody(MINIMAL_RESPONSE)));

            fetcher.fetchProductData(TEST_URL);

            wireMock.verify(postRequestedFor(urlPathEqualTo("/")));
        }

        @Test
        void sendsJsonContentType() {
            wireMock.stubFor(post(urlPathEqualTo("/"))
                    .willReturn(aResponse().withStatus(200).withBody(MINIMAL_RESPONSE)));

            fetcher.fetchProductData(TEST_URL);

            wireMock.verify(postRequestedFor(urlPathEqualTo("/"))
                    .withHeader("Content-Type", containing("application/json")));
        }

        @Test
        void sendsRegionHeader() {
            wireMock.stubFor(post(urlPathEqualTo("/"))
                    .willReturn(aResponse().withStatus(200).withBody(MINIMAL_RESPONSE)));

            fetcher.fetchProductData(TEST_URL);

            wireMock.verify(postRequestedFor(urlPathEqualTo("/"))
                    .withHeader("region", equalTo("IE")));
        }

        @Test
        void sendsApiKeyHeader() {
            wireMock.stubFor(post(urlPathEqualTo("/"))
                    .willReturn(aResponse().withStatus(200).withBody(MINIMAL_RESPONSE)));

            fetcher.fetchProductData(TEST_URL);

            wireMock.verify(postRequestedFor(urlPathEqualTo("/"))
                    .withHeader("x-apikey", equalTo("test-api-key")));
        }

        @Test
        void requestBodyIsJsonArray() throws Exception {
            wireMock.stubFor(post(urlPathEqualTo("/"))
                    .willReturn(aResponse().withStatus(200).withBody(MINIMAL_RESPONSE)));

            fetcher.fetchProductData(TEST_URL);

            var body = capturedRequestBody();
            assertTrue(body.isArray());
            assertEquals(1, body.size());
        }

        @Test
        void requestBodyHasOperationName() throws Exception {
            wireMock.stubFor(post(urlPathEqualTo("/"))
                    .willReturn(aResponse().withStatus(200).withBody(MINIMAL_RESPONSE)));

            fetcher.fetchProductData(TEST_URL);

            var body = capturedRequestBody().get(0);
            assertEquals("GetProduct", body.get("operationName").asText());
        }

        @Test
        void requestBodyHasTpncVariable() throws Exception {
            wireMock.stubFor(post(urlPathEqualTo("/"))
                    .willReturn(aResponse().withStatus(200).withBody(MINIMAL_RESPONSE)));

            fetcher.fetchProductData(TEST_URL);

            var variables = capturedRequestBody().get(0).get("variables");
            assertEquals("317328050", variables.get("tpnc").asText());
        }

        @Test
        void requestBodyHasSkipReviewsVariable() throws Exception {
            wireMock.stubFor(post(urlPathEqualTo("/"))
                    .willReturn(aResponse().withStatus(200).withBody(MINIMAL_RESPONSE)));

            fetcher.fetchProductData(TEST_URL);

            var variables = capturedRequestBody().get(0).get("variables");
            assertTrue(variables.get("skipReviews").asBoolean());
        }

        @Test
        void requestBodyHasQueryString() throws Exception {
            wireMock.stubFor(post(urlPathEqualTo("/"))
                    .willReturn(aResponse().withStatus(200).withBody(MINIMAL_RESPONSE)));

            fetcher.fetchProductData(TEST_URL);

            var body = capturedRequestBody().get(0);
            var query = body.get("query").asText();
            assertTrue(query.contains("GetProduct"));
            assertTrue(query.contains("tpnc"));
            assertTrue(query.contains("price"));
            assertTrue(query.contains("media"));
        }
    }

    @Nested
    class ResponseContract {

        @Test
        void fullResponse_returnsProductData() {
            wireMock.stubFor(post(urlPathEqualTo("/"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                    [{"data":{"product":{"title":"Oats","price":{"actual":3.49},"media":{"defaultImage":{"url":"https://img.tesco.com/img.jpg"}}}}}]
                                    """)));

            var result = fetcher.fetchProductData(TEST_URL);

            assertTrue(result.isPresent());
            var data = result.get();
            assertEquals(3.49f, data.price(), 0.001f);
            assertEquals(java.util.List.of("https://img.tesco.com/img.jpg"), data.imageUrls());
        }

        @Test
        void priceOnly_returnsProductDataWithPrice() {
            wireMock.stubFor(post(urlPathEqualTo("/"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                    [{"data":{"product":{"title":"Oats","price":{"actual":5.99},"media":{}}}}]
                                    """)));

            var result = fetcher.fetchProductData(TEST_URL);

            assertTrue(result.isPresent());
            assertEquals(5.99f, result.get().price(), 0.001f);
            assertTrue(result.get().imageUrls().isEmpty());
        }

        @Test
        void imageOnly_returnsProductDataWithImage() {
            wireMock.stubFor(post(urlPathEqualTo("/"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                    [{"data":{"product":{"title":"Oats","media":{"defaultImage":{"url":"https://img.tesco.com/img.jpg"}}}}}]
                                    """)));

            var result = fetcher.fetchProductData(TEST_URL);

            assertTrue(result.isPresent());
            assertEquals(java.util.List.of("https://img.tesco.com/img.jpg"), result.get().imageUrls());
        }

        @Test
        void noPriceNoImage_returnsEmpty() {
            wireMock.stubFor(post(urlPathEqualTo("/"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                    [{"data":{"product":{"title":"Oats","media":{}}}}]
                                    """)));

            var result = fetcher.fetchProductData(TEST_URL);

            assertTrue(result.isEmpty());
        }

        @Test
        void emptyJsonArray_returnsEmpty() {
            wireMock.stubFor(post(urlPathEqualTo("/"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("[]")));

            var result = fetcher.fetchProductData(TEST_URL);

            assertTrue(result.isEmpty());
        }

        @Test
        void nullBody_returnsEmpty() {
            wireMock.stubFor(post(urlPathEqualTo("/"))
                    .willReturn(aResponse().withStatus(200)));

            var result = fetcher.fetchProductData(TEST_URL);

            assertTrue(result.isEmpty());
        }

        @Test
        void blankBody_returnsEmpty() {
            wireMock.stubFor(post(urlPathEqualTo("/"))
                    .willReturn(aResponse().withStatus(200).withBody("   ")));

            var result = fetcher.fetchProductData(TEST_URL);

            assertTrue(result.isEmpty());
        }

        @Test
        void http400_returnsEmpty() {
            wireMock.stubFor(post(urlPathEqualTo("/"))
                    .willReturn(aResponse().withStatus(400).withBody("bad request")));

            var result = fetcher.fetchProductData(TEST_URL);

            assertTrue(result.isEmpty());
        }

        @Test
        void http500_returnsEmpty() {
            wireMock.stubFor(post(urlPathEqualTo("/"))
                    .willReturn(aResponse().withStatus(500).withBody("server error")));

            var result = fetcher.fetchProductData(TEST_URL);

            assertTrue(result.isEmpty());
        }

        @Test
        void http429_returnsEmpty() {
            wireMock.stubFor(post(urlPathEqualTo("/"))
                    .willReturn(aResponse().withStatus(429).withBody("rate limited")));

            var result = fetcher.fetchProductData(TEST_URL);

            assertTrue(result.isEmpty());
        }

        @Test
        void http503_returnsEmpty() {
            wireMock.stubFor(post(urlPathEqualTo("/"))
                    .willReturn(aResponse().withStatus(503).withBody("service unavailable")));

            var result = fetcher.fetchProductData(TEST_URL);

            assertTrue(result.isEmpty());
        }

        @Test
        void slowResponse_exceedsReadTimeout_returnsEmpty() {
            var requestFactory = new SimpleClientHttpRequestFactory();
            requestFactory.setReadTimeout(100);
            var timeoutRestTemplate = new RestTemplate(requestFactory);
            var timeoutFetcher = new TescoPriceFetcher(timeoutRestTemplate, "test-api-key", baseUrl);

            wireMock.stubFor(post(urlPathEqualTo("/"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withBody(MINIMAL_RESPONSE)
                            .withFixedDelay(1000)));

            var result = timeoutFetcher.fetchProductData(TEST_URL);

            assertTrue(result.isEmpty());
        }

    }

    @Nested
    class Retry403 {

        private static class TestableFetcher extends TescoPriceFetcher {
            private final String secondKey;

            TestableFetcher(RestTemplate restTemplate, String firstKey, String secondKey, String baseUrl) {
                super(restTemplate, firstKey, baseUrl);
                this.secondKey = secondKey;
            }

            @Override
            Optional<String> getApiKey(boolean forceRefresh) {
                if (forceRefresh) {
                    return Optional.ofNullable(secondKey);
                }
                return super.getApiKey(false);
            }
        }

        @Test
        void forbiddenResponse_triggersRetryWithNewKeyAndSucceeds() {
            String freshKey = "fresh-api-key";

            wireMock.stubFor(post(urlPathEqualTo("/"))
                    .withHeader("x-apikey", equalTo("test-api-key"))
                    .willReturn(aResponse().withStatus(403)));

            wireMock.stubFor(post(urlPathEqualTo("/"))
                    .withHeader("x-apikey", equalTo(freshKey))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                    [{"data":{"product":{"title":"Oats","price":{"actual":2.50},"media":{"defaultImage":{"url":"https://img.tesco.com/img.jpg"}}}}}]
                                    """)));

            var restTemplate = new org.springframework.web.client.RestTemplate();
            var fetcher = new TestableFetcher(restTemplate, "test-api-key", freshKey, baseUrl);

            var result = fetcher.fetchProductData(TEST_URL);

            assertTrue(result.isPresent());
            var data = result.get();
            assertEquals(2.50f, data.price(), 0.001f);
            assertEquals(List.of("https://img.tesco.com/img.jpg"), data.imageUrls());
            wireMock.verify(2, postRequestedFor(urlPathEqualTo("/")));
        }

        @Test
        void forbiddenResponse_retryKeyNotFound_returnsEmpty() {
            wireMock.stubFor(post(urlPathEqualTo("/"))
                    .withHeader("x-apikey", equalTo("test-api-key"))
                    .willReturn(aResponse().withStatus(403)));

            var restTemplate = new org.springframework.web.client.RestTemplate();
            var fetcher = new TestableFetcher(restTemplate, "test-api-key", null, baseUrl);

            var result = fetcher.fetchProductData(TEST_URL);

            assertTrue(result.isEmpty());
            wireMock.verify(1, postRequestedFor(urlPathEqualTo("/")));
        }

        @Test
        void forbiddenResponse_retryAlsoForbidden_returnsEmpty() {
            wireMock.stubFor(post(urlPathEqualTo("/"))
                    .withHeader("x-apikey", equalTo("test-api-key"))
                    .willReturn(aResponse().withStatus(403)));

            wireMock.stubFor(post(urlPathEqualTo("/"))
                    .withHeader("x-apikey", equalTo("new-key"))
                    .willReturn(aResponse().withStatus(403)));

            var restTemplate = new org.springframework.web.client.RestTemplate();
            var fetcher = new TestableFetcher(restTemplate, "test-api-key", "new-key", baseUrl);

            var result = fetcher.fetchProductData(TEST_URL);

            assertTrue(result.isEmpty());
            wireMock.verify(2, postRequestedFor(urlPathEqualTo("/")));
        }
    }

    private com.fasterxml.jackson.databind.JsonNode capturedRequestBody() {
        var requests = wireMock.findAll(postRequestedFor(urlPathEqualTo("/")));
        try {
            return objectMapper.readTree(requests.getFirst().getBodyAsString());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

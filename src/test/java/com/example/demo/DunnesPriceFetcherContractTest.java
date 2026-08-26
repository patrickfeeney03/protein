package com.example.demo;

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

class DunnesPriceFetcherContractTest {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(options().dynamicPort())
            .build();

    private DunnesPriceFetcher fetcher;
    private TestableDunnesPriceFetcher testableFetcher;
    private String baseUrl;

    // Matches STORE_ID_PATTERN = "/rsid/(\\d+)" and PRODUCT_ID_PATTERN = "id-(\\d+)"
    private static final String TEST_URL = "https://www.dunnesstoresgrocery.com/rsid/12345/product-name-id-67890";
    private static final String STORE_ID = "12345";
    private static final String PRODUCT_ID = "67890";
    private static final String API_PATH = "/api/stores/" + STORE_ID + "/products/" + PRODUCT_ID;

    private static final String FULL_RESPONSE = """
            {
              "price": "3.39",
              "primaryImage": {
                "zoom": "https://images.dunnesstoresgrocery.com/zoom/test.jpg"
              }
            }
            """;

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + wireMock.getPort();
        var restTemplate = new RestTemplate();
        fetcher = new DunnesPriceFetcher(restTemplate, baseUrl);
        testableFetcher = new TestableDunnesPriceFetcher(restTemplate, baseUrl);
    }

    @Nested
    class RequestContract {

        @Test
        void sendsGetToApiPath() {
            wireMock.stubFor(get(urlPathEqualTo(API_PATH))
                    .willReturn(aResponse().withStatus(200).withBody(FULL_RESPONSE)));

            fetcher.fetchProductData(TEST_URL);

            wireMock.verify(getRequestedFor(urlPathEqualTo(API_PATH)));
        }

        @Test
        void stripsFragmentAndQueryFromUrl() {
            wireMock.stubFor(get(urlPathEqualTo(API_PATH))
                    .willReturn(aResponse().withStatus(200).withBody(FULL_RESPONSE)));

            var urlWithFragment = TEST_URL + "#section";
            var urlWithQuery = TEST_URL + "?param=value";
            fetcher.fetchProductData(urlWithFragment);
            fetcher.fetchProductData(urlWithQuery);

            wireMock.verify(2, getRequestedFor(urlPathEqualTo(API_PATH)));
        }

        @Test
        void returnsEmptyWhenStoreIdMissing() {
            var urlWithoutStore = "https://www.dunnesstoresgrocery.com/product-name-id-67890";

            var result = fetcher.fetchProductData(urlWithoutStore);

            assertTrue(result.isEmpty());
        }

        @Test
        void returnsEmptyWhenProductIdMissing() {
            var urlWithoutProduct = "https://www.dunnesstoresgrocery.com/rsid/12345/product-name";

            var result = fetcher.fetchProductData(urlWithoutProduct);

            assertTrue(result.isEmpty());
        }
    }

    @Nested
    class ResponseContract {

        @Test
        void fullResponse_returnsProductData() {
            wireMock.stubFor(get(urlPathEqualTo(API_PATH))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(FULL_RESPONSE)));

            var result = fetcher.fetchProductData(TEST_URL);

            assertTrue(result.isPresent());
            var data = result.get();
            assertEquals(3.39f, data.price(), 0.001f);
            assertEquals(List.of("https://images.dunnesstoresgrocery.com/zoom/test.jpg"), data.imageUrls());
        }

        @Test
        void priceOnly_returnsProductDataWithPrice() {
            wireMock.stubFor(get(urlPathEqualTo(API_PATH))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                    {
                                      "price": "5.99"
                                    }
                                    """)));

            var result = fetcher.fetchProductData(TEST_URL);

            assertTrue(result.isPresent());
            assertEquals(5.99f, result.get().price(), 0.001f);
            assertTrue(result.get().imageUrls().isEmpty());
        }

        @Test
        void imageOnly_returnsProductDataWithImage() {
            wireMock.stubFor(get(urlPathEqualTo(API_PATH))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                    {
                                      "primaryImage": {
                                        "zoom": "https://images.dunnesstoresgrocery.com/zoom/test.jpg"
                                      }
                                    }
                                    """)));

            var result = fetcher.fetchProductData(TEST_URL);

            assertTrue(result.isPresent());
            assertNull(result.get().price());
            assertEquals(List.of("https://images.dunnesstoresgrocery.com/zoom/test.jpg"), result.get().imageUrls());
        }

        @Test
        void imageOnly_fallsBackThroughPrimaryImageChain() {
            wireMock.stubFor(get(urlPathEqualTo(API_PATH))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                    {
                                      "primaryImage": {
                                        "default": "https://images.dunnesstoresgrocery.com/default/test.jpg"
                                      }
                                    }
                                    """)));

            var result = fetcher.fetchProductData(TEST_URL);

            assertTrue(result.isPresent());
            assertEquals(List.of("https://images.dunnesstoresgrocery.com/default/test.jpg"), result.get().imageUrls());
        }

        @Test
        void imageOnly_fallsBackToAdditionalImages() {
            wireMock.stubFor(get(urlPathEqualTo(API_PATH))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                    {
                                      "additionalImages": {
                                        "zoom": ["https://images.dunnesstoresgrocery.com/additional-zoom.jpg"]
                                      }
                                    }
                                    """)));

            var result = fetcher.fetchProductData(TEST_URL);

            assertTrue(result.isPresent());
            assertEquals(List.of("https://images.dunnesstoresgrocery.com/additional-zoom.jpg"), result.get().imageUrls());
        }

        @Test
        void noPriceNoImage_returnsEmpty() {
            wireMock.stubFor(get(urlPathEqualTo(API_PATH))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("{}")));

            var result = fetcher.fetchProductData(TEST_URL);

            assertTrue(result.isEmpty());
        }

        @Test
        void emptyJsonArray_returnsEmpty() {
            wireMock.stubFor(get(urlPathEqualTo(API_PATH))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("[]")));

            var result = fetcher.fetchProductData(TEST_URL);

            assertTrue(result.isEmpty());
        }

        @Test
        void nullBody_returnsEmpty() {
            wireMock.stubFor(get(urlPathEqualTo(API_PATH))
                    .willReturn(aResponse().withStatus(200)));

            var result = fetcher.fetchProductData(TEST_URL);

            assertTrue(result.isEmpty());
        }

        @Test
        void blankBody_returnsEmpty() {
            wireMock.stubFor(get(urlPathEqualTo(API_PATH))
                    .willReturn(aResponse().withStatus(200).withBody("   ")));

            var result = fetcher.fetchProductData(TEST_URL);

            assertTrue(result.isEmpty());
        }

        @Test
        void http400_returnsEmpty() {
            wireMock.stubFor(get(urlPathEqualTo(API_PATH))
                    .willReturn(aResponse().withStatus(400).withBody("bad request")));

            var result = testableFetcher.fetchProductData(TEST_URL);

            assertTrue(result.isEmpty());
        }

        @Test
        void http500_returnsEmpty() {
            wireMock.stubFor(get(urlPathEqualTo(API_PATH))
                    .willReturn(aResponse().withStatus(500).withBody("server error")));

            var result = testableFetcher.fetchProductData(TEST_URL);

            assertTrue(result.isEmpty());
        }

        @Test
        void http429_returnsEmpty() {
            wireMock.stubFor(get(urlPathEqualTo(API_PATH))
                    .willReturn(aResponse().withStatus(429).withBody("rate limited")));

            var result = testableFetcher.fetchProductData(TEST_URL);

            assertTrue(result.isEmpty());
        }

        @Test
        void http503_returnsEmpty() {
            wireMock.stubFor(get(urlPathEqualTo(API_PATH))
                    .willReturn(aResponse().withStatus(503).withBody("service unavailable")));

            var result = testableFetcher.fetchProductData(TEST_URL);

            assertTrue(result.isEmpty());
        }

        @Test
        void slowResponse_exceedsReadTimeout_returnsEmpty() {
            var requestFactory = new SimpleClientHttpRequestFactory();
            requestFactory.setReadTimeout(100);
            var timeoutRestTemplate = new RestTemplate(requestFactory);
            var timeoutFetcher = new TestableDunnesPriceFetcher(timeoutRestTemplate, baseUrl);

            wireMock.stubFor(get(urlPathEqualTo(API_PATH))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withBody(FULL_RESPONSE)
                            .withFixedDelay(1000)));

            var result = timeoutFetcher.fetchProductData(TEST_URL);

            assertTrue(result.isEmpty());
        }
    }

    @Nested
    class HttpErrorWithBrowserFallback {

        @Test
        void http403_triggersBrowserFallback_returnsData() {
            wireMock.stubFor(get(urlPathEqualTo(API_PATH))
                    .willReturn(aResponse().withStatus(403).withBody("forbidden")));

            var browserFetcher = new TestableDunnesPriceFetcherWithBrowserData(restTemplate(), baseUrl);
            var result = browserFetcher.fetchProductData(TEST_URL);

            assertTrue(result.isPresent());
            assertEquals(3.39f, result.get().price(), 0.001f);
            assertEquals(List.of("https://images.dunnesstoresgrocery.com/zoom/test.jpg"), result.get().imageUrls());
        }

        @Test
        void http400_triggersBrowserFallback_returnsData() {
            wireMock.stubFor(get(urlPathEqualTo(API_PATH))
                    .willReturn(aResponse().withStatus(400).withBody("bad request")));

            var browserFetcher = new TestableDunnesPriceFetcherWithBrowserData(restTemplate(), baseUrl);
            var result = browserFetcher.fetchProductData(TEST_URL);

            assertTrue(result.isPresent());
            assertEquals(3.39f, result.get().price(), 0.001f);
        }

        @Test
        void http500_triggersBrowserFallback_returnsData() {
            wireMock.stubFor(get(urlPathEqualTo(API_PATH))
                    .willReturn(aResponse().withStatus(500).withBody("server error")));

            var browserFetcher = new TestableDunnesPriceFetcherWithBrowserData(restTemplate(), baseUrl);
            var result = browserFetcher.fetchProductData(TEST_URL);

            assertTrue(result.isPresent());
            assertEquals(3.39f, result.get().price(), 0.001f);
        }

        @Test
        void http429_triggersBrowserFallback_returnsData() {
            wireMock.stubFor(get(urlPathEqualTo(API_PATH))
                    .willReturn(aResponse().withStatus(429).withBody("rate limited")));

            var browserFetcher = new TestableDunnesPriceFetcherWithBrowserData(restTemplate(), baseUrl);
            var result = browserFetcher.fetchProductData(TEST_URL);

            assertTrue(result.isPresent());
            assertEquals(3.39f, result.get().price(), 0.001f);
        }

        @Test
        void http503_triggersBrowserFallback_returnsData() {
            wireMock.stubFor(get(urlPathEqualTo(API_PATH))
                    .willReturn(aResponse().withStatus(503).withBody("service unavailable")));

            var browserFetcher = new TestableDunnesPriceFetcherWithBrowserData(restTemplate(), baseUrl);
            var result = browserFetcher.fetchProductData(TEST_URL);

            assertTrue(result.isPresent());
            assertEquals(3.39f, result.get().price(), 0.001f);
        }
    }

    static class TestableDunnesPriceFetcher extends DunnesPriceFetcher {

        TestableDunnesPriceFetcher(RestTemplate restTemplate, String baseUrl) {
            super(restTemplate, baseUrl);
        }

        @Override
        Optional<DunnesProductData> fetchProductDataWithBrowser(String productUrl, String apiUrl) {
            return Optional.empty();
        }
    }

    static class TestableDunnesPriceFetcherWithBrowserData extends DunnesPriceFetcher {

        TestableDunnesPriceFetcherWithBrowserData(RestTemplate restTemplate, String baseUrl) {
            super(restTemplate, baseUrl);
        }

        @Override
        Optional<DunnesProductData> fetchProductDataWithBrowser(String productUrl, String apiUrl) {
            return Optional.of(new DunnesProductData(3.39f, List.of("https://images.dunnesstoresgrocery.com/zoom/test.jpg")));
        }
    }

    private RestTemplate restTemplate() {
        return new RestTemplate();
    }
}

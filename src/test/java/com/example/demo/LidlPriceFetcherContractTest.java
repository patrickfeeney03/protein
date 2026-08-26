package com.example.demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.List;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.jupiter.api.Assertions.*;

class LidlPriceFetcherContractTest {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(options().dynamicPort())
            .build();

    private final ObjectMapper objectMapper = new ObjectMapper();
    private LidlPriceFetcher fetcher;
    private String baseUrl;

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + wireMock.getPort();
        var restTemplate = new RestTemplate();
        fetcher = new LidlPriceFetcher(restTemplate, baseUrl);
    }

    private static final String TEST_URL = "https://www.lidl.ie/p12345";
    private static final String PRODUCT_ID = "12345";

    private static final String FULL_RESPONSE = """
            {
              "root": {
                "items": [
                  {
                    "gridbox": {
                      "data": {
                        "price": {
                          "price": 3.49
                        },
                        "imageList": [
                          "https://img.lidl.ie/first.jpg",
                          "https://img.lidl.ie/second.jpg"
                        ]
                      }
                    }
                  }
                ]
              }
            }
            """;

    @Nested
    class RequestContract {

        @Test
        void sendsGetToApiPath() {
            wireMock.stubFor(get(urlPathEqualTo("/q/api/search"))
                    .willReturn(aResponse().withStatus(200).withBody(FULL_RESPONSE)));

            fetcher.fetchProductData(TEST_URL);

            wireMock.verify(getRequestedFor(urlPathEqualTo("/q/api/search")));
        }

        @Test
        void sendsProductIdAsQueryParam() {
            wireMock.stubFor(get(urlPathEqualTo("/q/api/search"))
                    .willReturn(aResponse().withStatus(200).withBody(FULL_RESPONSE)));

            fetcher.fetchProductData(TEST_URL);

            wireMock.verify(getRequestedFor(urlPathEqualTo("/q/api/search"))
                    .withQueryParam("q", equalTo(PRODUCT_ID)));
        }

        @Test
        void sendsAssortmentQueryParam() {
            wireMock.stubFor(get(urlPathEqualTo("/q/api/search"))
                    .willReturn(aResponse().withStatus(200).withBody(FULL_RESPONSE)));

            fetcher.fetchProductData(TEST_URL);

            wireMock.verify(getRequestedFor(urlPathEqualTo("/q/api/search"))
                    .withQueryParam("assortment", equalTo("IE")));
        }

        @Test
        void sendsLocaleQueryParam() {
            wireMock.stubFor(get(urlPathEqualTo("/q/api/search"))
                    .willReturn(aResponse().withStatus(200).withBody(FULL_RESPONSE)));

            fetcher.fetchProductData(TEST_URL);

            wireMock.verify(getRequestedFor(urlPathEqualTo("/q/api/search"))
                    .withQueryParam("locale", equalTo("en_IE")));
        }

        @Test
        void sendsRedirectFalseQueryParam() {
            wireMock.stubFor(get(urlPathEqualTo("/q/api/search"))
                    .willReturn(aResponse().withStatus(200).withBody(FULL_RESPONSE)));

            fetcher.fetchProductData(TEST_URL);

            wireMock.verify(getRequestedFor(urlPathEqualTo("/q/api/search"))
                    .withQueryParam("redirect", equalTo("false")));
        }

        @Test
        void sendsVersionQueryParam() {
            wireMock.stubFor(get(urlPathEqualTo("/q/api/search"))
                    .willReturn(aResponse().withStatus(200).withBody(FULL_RESPONSE)));

            fetcher.fetchProductData(TEST_URL);

            wireMock.verify(getRequestedFor(urlPathEqualTo("/q/api/search"))
                    .withQueryParam("version", equalTo("v2.1.0")));
        }
    }

    @Nested
    class ResponseContract {

        @Test
        void fullResponse_returnsProductData() {
            wireMock.stubFor(get(urlPathEqualTo("/q/api/search"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(FULL_RESPONSE)));

            var result = fetcher.fetchProductData(TEST_URL);

            assertTrue(result.isPresent());
            var data = result.get();
            assertEquals(3.49f, data.price(), 0.001f);
            assertEquals(List.of("https://img.lidl.ie/second.jpg"), data.imageUrls());
        }

        @Test
        void priceOnly_returnsProductDataWithPrice() {
            wireMock.stubFor(get(urlPathEqualTo("/q/api/search"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                    {
                                      "root": {
                                        "items": [
                                          {
                                            "gridbox": {
                                              "data": {
                                                "price": {
                                                  "price": 5.99
                                                }
                                              }
                                            }
                                          }
                                        ]
                                      }
                                    }
                                    """)));

            var result = fetcher.fetchProductData(TEST_URL);

            assertTrue(result.isPresent());
            assertEquals(5.99f, result.get().price(), 0.001f);
            assertTrue(result.get().imageUrls().isEmpty());
        }

        @Test
        void imageOnly_returnsProductDataWithImages() {
            wireMock.stubFor(get(urlPathEqualTo("/q/api/search"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                    {
                                      "root": {
                                        "items": [
                                          {
                                            "gridbox": {
                                              "data": {
                                                "image": "https://img.lidl.ie/solo.jpg",
                                                "imageList": [
                                                  "https://img.lidl.ie/first.jpg"
                                                ]
                                              }
                                            }
                                          }
                                        ]
                                      }
                                    }
                                    """)));

            var result = fetcher.fetchProductData(TEST_URL);

            assertTrue(result.isPresent());
            assertNull(result.get().price());
            assertEquals(List.of("https://img.lidl.ie/solo.jpg"), result.get().imageUrls());
        }

        @Test
        void noPriceNoImage_returnsEmpty() {
            wireMock.stubFor(get(urlPathEqualTo("/q/api/search"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                    {
                                      "root": {
                                        "items": [
                                          {
                                            "gridbox": {
                                              "data": {}
                                            }
                                          }
                                        ]
                                      }
                                    }
                                    """)));

            var result = fetcher.fetchProductData(TEST_URL);

            assertTrue(result.isEmpty());
        }

        @Test
        void emptyJsonArray_returnsEmpty() {
            wireMock.stubFor(get(urlPathEqualTo("/q/api/search"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("[]")));

            var result = fetcher.fetchProductData(TEST_URL);

            assertTrue(result.isEmpty());
        }

        @Test
        void nullBody_returnsEmpty() {
            wireMock.stubFor(get(urlPathEqualTo("/q/api/search"))
                    .willReturn(aResponse().withStatus(200)));

            var result = fetcher.fetchProductData(TEST_URL);

            assertTrue(result.isEmpty());
        }

        @Test
        void blankBody_returnsEmpty() {
            wireMock.stubFor(get(urlPathEqualTo("/q/api/search"))
                    .willReturn(aResponse().withStatus(200).withBody("   ")));

            var result = fetcher.fetchProductData(TEST_URL);

            assertTrue(result.isEmpty());
        }

        @Test
        void http400_returnsEmpty() {
            wireMock.stubFor(get(urlPathEqualTo("/q/api/search"))
                    .willReturn(aResponse().withStatus(400).withBody("bad request")));

            var result = fetcher.fetchProductData(TEST_URL);

            assertTrue(result.isEmpty());
        }

        @Test
        void http500_returnsEmpty() {
            wireMock.stubFor(get(urlPathEqualTo("/q/api/search"))
                    .willReturn(aResponse().withStatus(500).withBody("server error")));

            var result = fetcher.fetchProductData(TEST_URL);

            assertTrue(result.isEmpty());
        }

        @Test
        void http429_returnsEmpty() {
            wireMock.stubFor(get(urlPathEqualTo("/q/api/search"))
                    .willReturn(aResponse().withStatus(429).withBody("rate limited")));

            var result = fetcher.fetchProductData(TEST_URL);

            assertTrue(result.isEmpty());
        }

        @Test
        void http503_returnsEmpty() {
            wireMock.stubFor(get(urlPathEqualTo("/q/api/search"))
                    .willReturn(aResponse().withStatus(503).withBody("service unavailable")));

            var result = fetcher.fetchProductData(TEST_URL);

            assertTrue(result.isEmpty());
        }

        @Test
        void slowResponse_exceedsReadTimeout_returnsEmpty() {
            var requestFactory = new SimpleClientHttpRequestFactory();
            requestFactory.setReadTimeout(100);
            var timeoutRestTemplate = new RestTemplate(requestFactory);
            var timeoutFetcher = new LidlPriceFetcher(timeoutRestTemplate, baseUrl);

            wireMock.stubFor(get(urlPathEqualTo("/q/api/search"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withBody(FULL_RESPONSE)
                            .withFixedDelay(1000)));

            var result = timeoutFetcher.fetchProductData(TEST_URL);

            assertTrue(result.isEmpty());
        }
    }
}

package com.example.demo;

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

class AldiPriceFetcherContractTest {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(options().dynamicPort())
            .build();

    private AldiPriceFetcher fetcher;
    private String baseUrl;

    // Matches SKU_PATTERN = "-(\\d+)$"
    private static final String TEST_URL = "https://groceries.aldi.ie/en_IE/p-product-name-12345";
    private static final String SKU = "12345";
    private static final String API_PATH = "/v2/products/" + SKU;

    private static final String ASSET_URL_TEMPLATE = "https://dm.emea.cms.aldi.cx/is/image/aldiprodeu/product/jpg/scaleWidth/{width}/asset-id/{slug}";
    private static final String MATERIALIZED_URL = "https://dm.emea.cms.aldi.cx/is/image/aldiprodeu/product/jpg/scaleWidth/1500/asset-id";

    private static final String FULL_RESPONSE = """
            {
              "data": {
                "price": {
                  "amount": 249
                },
                "assets": [
                  {
                    "url": "%s",
                    "maxWidth": "1500"
                  }
                ]
              }
            }
            """.formatted(ASSET_URL_TEMPLATE);

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + wireMock.getPort();
        var restTemplate = new RestTemplate();
        fetcher = new AldiPriceFetcher(restTemplate, baseUrl);
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
        void sendsServicePointQueryParam() {
            wireMock.stubFor(get(urlPathEqualTo(API_PATH))
                    .willReturn(aResponse().withStatus(200).withBody(FULL_RESPONSE)));

            fetcher.fetchProductData(TEST_URL);

            wireMock.verify(getRequestedFor(urlPathEqualTo(API_PATH))
                    .withQueryParam("servicePoint", equalTo("D105")));
        }

        @Test
        void sendsServiceTypeQueryParam() {
            wireMock.stubFor(get(urlPathEqualTo(API_PATH))
                    .willReturn(aResponse().withStatus(200).withBody(FULL_RESPONSE)));

            fetcher.fetchProductData(TEST_URL);

            wireMock.verify(getRequestedFor(urlPathEqualTo(API_PATH))
                    .withQueryParam("serviceType", equalTo("walk-in")));
        }

        @Test
        void returnsEmptyWhenSkuNotInUrl() {
            var urlWithoutSku = "https://groceries.aldi.ie/en_IE/p-product-name";

            var result = fetcher.fetchProductData(urlWithoutSku);

            assertTrue(result.isEmpty());
            wireMock.verify(0, getRequestedFor(anyUrl()));
        }

        @Test
        void returnsEmptyWhenUrlHasNoHyphenPrefix() {
            var urlWithLeadingDigits = "https://groceries.aldi.ie/en_IE/product12345";

            var result = fetcher.fetchProductData(urlWithLeadingDigits);

            assertTrue(result.isEmpty());
            wireMock.verify(0, getRequestedFor(anyUrl()));
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
            assertEquals(2.49f, data.price(), 0.001f);
            assertEquals(List.of(MATERIALIZED_URL), data.imageUrls());
        }

        @Test
        void priceOnly_returnsProductDataWithPrice() {
            wireMock.stubFor(get(urlPathEqualTo(API_PATH))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                    {
                                      "data": {
                                        "price": {
                                          "amount": 549
                                        }
                                      }
                                    }
                                    """)));

            var result = fetcher.fetchProductData(TEST_URL);

            assertTrue(result.isPresent());
            assertEquals(5.49f, result.get().price(), 0.001f);
            assertTrue(result.get().imageUrls().isEmpty());
        }

        @Test
        void imageOnly_returnsProductDataWithImages() {
            wireMock.stubFor(get(urlPathEqualTo(API_PATH))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                    {
                                      "data": {
                                        "assets": [
                                          {
                                            "url": "%s",
                                            "maxWidth": "1500"
                                          }
                                        ]
                                      }
                                    }
                                    """.formatted(ASSET_URL_TEMPLATE))));

            var result = fetcher.fetchProductData(TEST_URL);

            assertTrue(result.isPresent());
            assertNull(result.get().price());
            assertEquals(List.of(MATERIALIZED_URL), result.get().imageUrls());
        }

        @Test
        void multipleImages_returnsAllMaterializedUrls() {
            var secondMaterialized = "https://dm.emea.cms.aldi.cx/is/image/aldiprodeu/product/jpg/scaleWidth/500/asset-id";

            wireMock.stubFor(get(urlPathEqualTo(API_PATH))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                    {
                                      "data": {
                                        "price": {
                                          "amount": 249
                                        },
                                        "assets": [
                                          {
                                            "url": "%s",
                                            "maxWidth": "1500"
                                          },
                                          {
                                            "url": "%s",
                                            "maxWidth": "500"
                                          }
                                        ]
                                      }
                                    }
                                    """.formatted(ASSET_URL_TEMPLATE, ASSET_URL_TEMPLATE))));

            var result = fetcher.fetchProductData(TEST_URL);

            assertTrue(result.isPresent());
            assertEquals(2.49f, result.get().price(), 0.001f);
            assertEquals(List.of(MATERIALIZED_URL, secondMaterialized), result.get().imageUrls());
        }

        @Test
        void noPriceNoImage_returnsEmpty() {
            wireMock.stubFor(get(urlPathEqualTo(API_PATH))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                    {
                                      "data": {}
                                    }
                                    """)));

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

            var result = fetcher.fetchProductData(TEST_URL);

            assertTrue(result.isEmpty());
        }

        @Test
        void http500_returnsEmpty() {
            wireMock.stubFor(get(urlPathEqualTo(API_PATH))
                    .willReturn(aResponse().withStatus(500).withBody("server error")));

            var result = fetcher.fetchProductData(TEST_URL);

            assertTrue(result.isEmpty());
        }

        @Test
        void http429_returnsEmpty() {
            wireMock.stubFor(get(urlPathEqualTo(API_PATH))
                    .willReturn(aResponse().withStatus(429).withBody("rate limited")));

            var result = fetcher.fetchProductData(TEST_URL);

            assertTrue(result.isEmpty());
        }

        @Test
        void http503_returnsEmpty() {
            wireMock.stubFor(get(urlPathEqualTo(API_PATH))
                    .willReturn(aResponse().withStatus(503).withBody("service unavailable")));

            var result = fetcher.fetchProductData(TEST_URL);

            assertTrue(result.isEmpty());
        }

        @Test
        void slowResponse_exceedsReadTimeout_returnsEmpty() {
            var requestFactory = new SimpleClientHttpRequestFactory();
            requestFactory.setReadTimeout(100);
            var timeoutRestTemplate = new RestTemplate(requestFactory);
            var timeoutFetcher = new AldiPriceFetcher(timeoutRestTemplate, baseUrl);

            wireMock.stubFor(get(urlPathEqualTo(API_PATH))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withBody(FULL_RESPONSE)
                            .withFixedDelay(1000)));

            var result = timeoutFetcher.fetchProductData(TEST_URL);

            assertTrue(result.isEmpty());
        }
    }
}

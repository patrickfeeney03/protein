package com.example.demo.services;

import com.example.demo.NutritionScannerProperties;
import com.example.demo.entities.FoodEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.time.Duration;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.jupiter.api.Assertions.*;

class NutritionScanServiceIntegrationTest {

    private WireMockServer wireMock;
    private NutritionScanService service;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(options().dynamicPort());
        wireMock.start();
        var properties = new NutritionScannerProperties();
        properties.setBaseUrl("http://localhost:" + wireMock.port());
        properties.setApiKey("test-key");
        properties.setTimeout(Duration.ofSeconds(5));
        service = new NutritionScanService(new ObjectMapper(), properties);
    }

    @AfterEach
    void tearDown() {
        if (wireMock != null) {
            wireMock.stop();
        }
    }

    @Test
    void scan_successWithAnnotation() throws Exception {
        wireMock.stubFor(post(urlPathEqualTo("/v1/ocr"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "pages": [
                                    {
                                      "index": 0,
                                      "markdown": "# Oats"
                                    }
                                  ],
                                  "document_annotation": "{\\"name\\":\\"Oats\\",\\"brand\\":\\"Brand\\",\\"store_name\\":\\"Aldi\\",\\"servings_per_container\\":3,\\"total_weight\\":1200,\\"total_weight_unit\\":\\"g\\",\\"serving_size\\":40,\\"serving_unit\\":\\"g\\",\\"macros_per_serving\\":{\\"energy_kcal\\":142,\\"fat_g\\":1.7,\\"carbohydrate_g\\":24.7,\\"protein_g\\":4.9},\\"macros_per_100\\":{\\"energy_kj\\":1494,\\"energy_kcal\\":354,\\"fat_g\\":4.1,\\"carbohydrate_g\\":61.7,\\"protein_g\\":12.2}}"
                                }
                                """)));

        var image = new MockMultipartFile("images", "label.png", "image/png", new byte[]{1});
        var result = service.scan(List.of(image));

        assertTrue(result.scanSucceeded());
        assertEquals(40f, result.parsed().servingSize());
        assertEquals(FoodEntity.Unit.G, result.parsed().servingUnit());
        assertEquals(142f, result.parsed().caloriesPerServing());
        assertEquals(354f, result.parsed().caloriesPer100());
        assertEquals("Oats", result.product().name());
        assertEquals("Brand", result.product().brand());
        assertEquals("Aldi", result.product().storeName());
        assertEquals(3f, result.product().servingsPerContainer());
        assertEquals(1200f, result.product().totalWeight());
        assertEquals(FoodEntity.Unit.G, result.product().totalWeightUnit());
        assertEquals(ScanSource.ANNOTATION, result.sourceUsed());
        assertFalse(result.usedAnnotationFallback());
    }

    @Test
    void scan_successWithTable() throws Exception {
        wireMock.stubFor(post(urlPathEqualTo("/v1/ocr"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "pages": [
                                    {
                                      "index": 0,
                                      "markdown": "NUTRITION\\n\\n[tbl-0.html](tbl-0.html)",
                                      "tables": [
                                        {
                                          "id": "tbl-0.html",
                                          "content": "<table><tr><th>TYPICAL VALUES</th><th>Per 100g</th><th>Per 1/2 can / approx. 120g</th></tr><tr><td>Energy</td><td>535kJ/127kcal</td><td>700kJ/30kcal</td></tr><tr><td>Fat</td><td>2.2g</td><td>2.8g</td></tr><tr><td>Carbohydrate</td><td>16.0g</td><td>16.0g</td></tr><tr><td>Protein</td><td>7.3g</td><td>6.9g</td></tr></table>"
                                        }
                                      ]
                                    }
                                  ],
                                  "document_annotation": ""
                                }
                                """)));

        var image = new MockMultipartFile("images", "label.png", "image/png", new byte[]{1});
        var result = service.scan(List.of(image));

        assertTrue(result.scanSucceeded());
        assertEquals(FoodEntity.Unit.G, result.parsed().servingUnit());
        assertEquals(120f, result.parsed().servingSize());
        assertEquals(127f, result.parsed().caloriesPer100());
        assertEquals(30f, result.parsed().caloriesPerServing());
        assertEquals(ScanSource.RAW_TABLE, result.sourceUsed());
    }

    @Test
    void scan_httpError_returnsFailed() throws Exception {
        wireMock.stubFor(post(urlPathEqualTo("/v1/ocr"))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"server error\"}")));

        var image = new MockMultipartFile("images", "label.png", "image/png", new byte[]{1});
        var result = service.scan(List.of(image));

        assertFalse(result.scanSucceeded());
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("500")));
    }

    @Test
    void scan_emptyBody_returnsFailed() throws Exception {
        wireMock.stubFor(post(urlPathEqualTo("/v1/ocr"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("")));

        var image = new MockMultipartFile("images", "label.png", "image/png", new byte[]{1});
        var result = service.scan(List.of(image));

        assertFalse(result.scanSucceeded());
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("empty response")));
    }

    @Test
    void scan_malformedJson_returnsFailed() throws Exception {
        wireMock.stubFor(post(urlPathEqualTo("/v1/ocr"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{invalid json}")));

        var image = new MockMultipartFile("images", "label.png", "image/png", new byte[]{1});
        var result = service.scan(List.of(image));

        assertFalse(result.scanSucceeded());
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("Unexpected character")));
    }

    @Test
    void scan_sendsCorrectRequestPayload() throws Exception {
        wireMock.stubFor(post(urlPathEqualTo("/v1/ocr"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"pages":[{"index":0,"markdown":"test"}]}
                                """)));

        var image = new MockMultipartFile("images", "label.png", "image/png", new byte[]{1, 2, 3});
        service.scan(List.of(image));

        var recorded = wireMock.findAll(postRequestedFor(urlPathEqualTo("/v1/ocr")));
        assertEquals(1, recorded.size());
        var request = recorded.getFirst();
        assertEquals("application/json", request.getHeader("Content-Type"));
        assertTrue(request.getHeader("Authorization").contains("Bearer test-key"));
        var body = new ObjectMapper().readTree(request.getBodyAsString());
        assertEquals("mistral-ocr-latest", body.get("model").asText());
        assertEquals("html", body.get("table_format").asText());
        assertTrue(body.get("document_annotation_prompt").asText().contains("Extract nutrition"));
        assertTrue(body.get("document").get("image_url").asText().startsWith("data:image/png;base64,"));
    }

    @Test
    void scan_multiImage_sendsTwoRequestsAndMerges() throws Exception {
        wireMock.stubFor(post(urlPathEqualTo("/v1/ocr"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "pages": [
                                    {
                                      "index": 0,
                                      "markdown": "# Oats"
                                    }
                                  ],
                                  "document_annotation": "{\\"name\\":\\"Oats\\",\\"brand\\":\\"Brand\\",\\"store_name\\":\\"Aldi\\",\\"servings_per_container\\":3,\\"total_weight\\":1200,\\"total_weight_unit\\":\\"g\\",\\"serving_size\\":40,\\"serving_unit\\":\\"g\\",\\"macros_per_serving\\":{\\"energy_kcal\\":142,\\"fat_g\\":1.7,\\"carbohydrate_g\\":24.7,\\"protein_g\\":4.9},\\"macros_per_100\\":{\\"energy_kj\\":1494,\\"energy_kcal\\":354,\\"fat_g\\":4.1,\\"carbohydrate_g\\":61.7,\\"protein_g\\":12.2}}"
                                }
                                """)));

        var image1 = new MockMultipartFile("images", "front.png", "image/png", new byte[]{1});
        var image2 = new MockMultipartFile("images", "back.png", "image/png", new byte[]{2});
        var result = service.scan(List.of(image1, image2));

        assertTrue(result.scanSucceeded());
        assertEquals("Oats", result.product().name());
        assertEquals(40f, result.parsed().servingSize());

        var recorded = wireMock.findAll(postRequestedFor(urlPathEqualTo("/v1/ocr")));
        assertEquals(2, recorded.size());
    }

    @Test
    void scan_annotationWithNullPer100_fallsBackToTable() throws Exception {
        wireMock.stubFor(post(urlPathEqualTo("/v1/ocr"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "pages": [
                                    {
                                      "index": 0,
                                      "markdown": "NUTRITION\\n\\n[tbl-0.html](tbl-0.html)",
                                      "tables": [
                                        {
                                          "id": "tbl-0.html",
                                          "content": "<table><tr><th>TYPICAL VALUES</th><th>Per 100g</th></tr><tr><td>Energy</td><td>535kJ/127kcal</td></tr><tr><td>Fat</td><td>2.2g</td></tr><tr><td>Carbohydrate</td><td>16.0g</td></tr><tr><td>Protein</td><td>7.3g</td></tr></table>"
                                        }
                                      ]
                                    }
                                  ],
                                  "document_annotation": "{\\"serving_size\\":40,\\"serving_unit\\":\\"g\\",\\"macros_per_serving\\":{\\"energy_kcal\\":142,\\"fat_g\\":1.7,\\"carbohydrate_g\\":24.7,\\"protein_g\\":4.9}}"
                                }
                                """)));

        var image = new MockMultipartFile("images", "label.png", "image/png", new byte[]{1});
        var result = service.scan(List.of(image));

        assertTrue(result.scanSucceeded());
        assertEquals(142f, result.parsed().caloriesPerServing());
        assertEquals(127f, result.parsed().caloriesPer100());
        assertEquals(1.7f, result.parsed().fatPerServing());
        assertEquals(2.2f, result.parsed().fatPer100());
        assertEquals(ScanSource.ANNOTATION, result.sourceUsed());
        assertTrue(result.usedAnnotationFallback());
    }

    @Test
    void scan_annotationWithOnlyPerServing_noPer100Source_preservesNullPer100() throws Exception {
        wireMock.stubFor(post(urlPathEqualTo("/v1/ocr"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "pages": [
                                    {
                                      "index": 0,
                                      "markdown": ""
                                    }
                                  ],
                                  "document_annotation": "{\\"name\\":\\"Oats\\",\\"brand\\":\\"Brand\\",\\"store_name\\":\\"Aldi\\",\\"serving_size\\":40,\\"serving_unit\\":\\"g\\",\\"macros_per_serving\\":{\\"energy_kcal\\":142,\\"fat_g\\":1.7,\\"carbohydrate_g\\":24.7,\\"protein_g\\":4.9}}"
                                }
                                """)));

        var image = new MockMultipartFile("images", "label.png", "image/png", new byte[]{1});
        var result = service.scan(List.of(image));

        assertTrue(result.scanSucceeded());

        assertEquals(142f, result.parsed().caloriesPerServing());
        assertEquals(1.7f, result.parsed().fatPerServing());
        assertEquals(24.7f, result.parsed().carbsPerServing());
        assertEquals(4.9f, result.parsed().proteinPerServing());
        assertEquals(40f, result.parsed().servingSize());
        assertEquals(FoodEntity.Unit.G, result.parsed().servingUnit());

        assertNull(result.parsed().caloriesPer100());
        assertNull(result.parsed().fatPer100());
        assertNull(result.parsed().carbsPer100());
        assertNull(result.parsed().proteinPer100());

        assertEquals("Oats", result.product().name());
        assertEquals("Brand", result.product().brand());
        assertEquals("Aldi", result.product().storeName());

        assertEquals(ScanSource.ANNOTATION, result.sourceUsed());
        assertFalse(result.usedAnnotationFallback());
        assertTrue(result.productUsedAnnotationFallback());
    }

    @Test
    void scan_annotationWithNullProductFields_fallsBackToText() throws Exception {
        wireMock.stubFor(post(urlPathEqualTo("/v1/ocr"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "pages": [
                                    {
                                      "index": 0,
                                      "markdown": "# Oats\\n\\nBrand: SomeBrand"
                                    }
                                  ],
                                  "document_annotation": "{\\"store_name\\":\\"Aldi\\",\\"servings_per_container\\":3,\\"total_weight\\":1200,\\"total_weight_unit\\":\\"g\\",\\"macros_per_100\\":{\\"energy_kj\\":1494,\\"energy_kcal\\":354,\\"fat_g\\":4.1,\\"carbohydrate_g\\":61.7,\\"protein_g\\":12.2}}"
                                }
                                """)));

        var image = new MockMultipartFile("images", "label.png", "image/png", new byte[]{1});
        var result = service.scan(List.of(image));

        assertTrue(result.scanSucceeded());
        assertEquals("Oats", result.product().name());
        assertEquals("SomeBrand", result.product().brand());
        assertEquals("Aldi", result.product().storeName());
        assertEquals(354f, result.parsed().caloriesPer100());
        assertEquals(ScanSource.ANNOTATION, result.sourceUsed());
    }

    @Test
    void scan_annotationDisagreement_usesRawValueAndAddsWarning() throws Exception {
        wireMock.stubFor(post(urlPathEqualTo("/v1/ocr"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "pages": [
                                    {
                                      "index": 0,
                                      "markdown": "NUTRITION\\n\\n[tbl-0.html](tbl-0.html)",
                                      "tables": [
                                        {
                                          "id": "tbl-0.html",
                                          "content": "<table><tr><th>TYPICAL VALUES</th><th>Per 100g</th><th>Per serving</th></tr><tr><td>Energy</td><td>535kJ/127kcal</td><td>214kJ/51kcal</td></tr><tr><td>Fat</td><td>2.2g</td><td>0.9g</td></tr><tr><td>Carbohydrate</td><td>16.0g</td><td>6.4g</td></tr><tr><td>Protein</td><td>7.3g</td><td>2.9g</td></tr></table>"
                                        }
                                      ]
                                    }
                                  ],
                                  "document_annotation": "{\\"macros_per_100\\":{\\"energy_kcal\\":354}}"
                                }
                                """)));

        var image = new MockMultipartFile("images", "label.png", "image/png", new byte[]{1});
        var result = service.scan(List.of(image));

        assertTrue(result.scanSucceeded());
        assertEquals(127f, result.parsed().caloriesPer100(), 0.001f);
        assertEquals(2.2f, result.parsed().fatPer100(), 0.001f);
        assertEquals(16.0f, result.parsed().carbsPer100(), 0.001f);
        assertEquals(7.3f, result.parsed().proteinPer100(), 0.001f);
        assertEquals(1, result.disagreements().size());
        assertTrue(result.disagreements().getFirst().contains("caloriesPer100"));
        assertEquals(ScanSource.ANNOTATION, result.sourceUsed());
    }

    @Test
    void scan_noUsableData_returnsFailedWithWarning() throws Exception {
        wireMock.stubFor(post(urlPathEqualTo("/v1/ocr"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "pages": [
                                    {
                                      "index": 0,
                                      "markdown": ""
                                    }
                                  ],
                                  "document_annotation": ""
                                }
                                """)));

        var image = new MockMultipartFile("images", "label.png", "image/png", new byte[]{1});
        var result = service.scan(List.of(image));

        assertFalse(result.scanSucceeded());
        assertTrue(result.warnings().contains("Unable to extract useful OCR values"));
        assertEquals(ScanSource.NONE, result.sourceUsed());
        assertEquals(ScanSource.NONE, result.productSourceUsed());
        assertFalse(result.usedAnnotationFallback());
        assertFalse(result.productUsedAnnotationFallback());
    }

    @Test
    void scan_unexpectedJsonStructure_returnsFailed() throws Exception {
        wireMock.stubFor(post(urlPathEqualTo("/v1/ocr"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"error":"rate limited","code":"too_many_requests"}
                                """)));

        var image = new MockMultipartFile("images", "label.png", "image/png", new byte[]{1});
        var result = service.scan(List.of(image));

        assertFalse(result.scanSucceeded());
        assertTrue(result.warnings().contains("Unable to extract useful OCR values"));
        assertEquals(ScanSource.NONE, result.sourceUsed());
        assertEquals(ScanSource.NONE, result.productSourceUsed());
    }

    @Test
    void scan_multiImage_differentResponses_mergesBestFromEachImage() throws Exception {
        wireMock.stubFor(post(urlPathEqualTo("/v1/ocr"))
                .withRequestBody(containing("base64,AQ"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "pages": [
                                    {
                                      "index": 0,
                                      "markdown": "# Peanut Butter"
                                    }
                                  ],
                                  "document_annotation": "{\\"name\\":\\"Peanut Butter\\",\\"brand\\":\\"Harvest Morn\\",\\"store_name\\":\\"Aldi\\"}"
                                }
                                """)));

        wireMock.stubFor(post(urlPathEqualTo("/v1/ocr"))
                .withRequestBody(containing("base64,Ag"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "pages": [
                                    {
                                      "index": 0,
                                      "markdown": "NUTRITION\\n\\n[tbl-0.html](tbl-0.html)",
                                      "tables": [
                                        {
                                          "id": "tbl-0.html",
                                          "content": "<table><tr><th>TYPICAL VALUES</th><th>Per 100g</th><th>Per serving (15g)</th></tr><tr><td>Energy</td><td>2500kJ/598kcal</td><td>375kJ/90kcal</td></tr><tr><td>Fat</td><td>49g</td><td>7.4g</td></tr><tr><td>Carbohydrate</td><td>20g</td><td>3g</td></tr><tr><td>Protein</td><td>24g</td><td>3.6g</td></tr></table>"
                                        }
                                      ]
                                    }
                                  ],
                                  "document_annotation": ""
                                }
                                """)));

        var image1 = new MockMultipartFile("images", "front.png", "image/png", new byte[]{1});
        var image2 = new MockMultipartFile("images", "back.png", "image/png", new byte[]{2});
        var result = service.scan(List.of(image1, image2));

        assertTrue(result.scanSucceeded());

        assertEquals("Peanut Butter", result.product().name());
        assertEquals("Harvest Morn", result.product().brand());
        assertEquals("Aldi", result.product().storeName());

        assertEquals(598f, result.parsed().caloriesPer100(), 0.001f);
        assertEquals(90f, result.parsed().caloriesPerServing(), 0.001f);
        assertEquals(49f, result.parsed().fatPer100(), 0.001f);
        assertEquals(7.4f, result.parsed().fatPerServing(), 0.001f);
        assertEquals(20f, result.parsed().carbsPer100(), 0.001f);
        assertEquals(24f, result.parsed().proteinPer100(), 0.001f);
        assertEquals(15f, result.parsed().servingSize(), 0.001f);
        assertEquals(FoodEntity.Unit.G, result.parsed().servingUnit());

        assertEquals(ScanSource.RAW_TABLE, result.sourceUsed());
        assertEquals(ScanSource.ANNOTATION, result.productSourceUsed());

        var recorded = wireMock.findAll(postRequestedFor(urlPathEqualTo("/v1/ocr")));
        assertEquals(2, recorded.size());
    }

    @Test
    void scan_multiImage_oneReturns429_returnsFailed() throws Exception {
        wireMock.stubFor(post(urlPathEqualTo("/v1/ocr"))
                .withRequestBody(containing("base64,AQ"))
                .willReturn(aResponse()
                        .withStatus(429)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"too many requests\"}")));

        wireMock.stubFor(post(urlPathEqualTo("/v1/ocr"))
                .withRequestBody(containing("base64,Ag"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "pages": [
                                    {
                                      "index": 0,
                                      "markdown": "# Oats"
                                    }
                                  ],
                                  "document_annotation": "{\\"name\\":\\"Oats\\",\\"brand\\":\\"Brand\\",\\"store_name\\":\\"Aldi\\"}"
                                }
                                """)));

        var image1 = new MockMultipartFile("images", "front.png", "image/png", new byte[]{1});
        var image2 = new MockMultipartFile("images", "back.png", "image/png", new byte[]{2});
        var result = service.scan(List.of(image1, image2));

        assertTrue(result.scanSucceeded());
        assertEquals("Oats", result.product().name());
        assertEquals("Brand", result.product().brand());
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("429")));
    }

    @Test
    void scan_multiImage_oneSlowResponseExceedsReadTimeout_returnsFailed() throws Exception {
        wireMock.stubFor(post(urlPathEqualTo("/v1/ocr"))
                .withRequestBody(containing("base64,AQ"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{}")
                        .withFixedDelay(1000)));

        wireMock.stubFor(post(urlPathEqualTo("/v1/ocr"))
                .withRequestBody(containing("base64,Ag"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "pages": [
                                    {
                                      "index": 0,
                                      "markdown": "# Oats"
                                    }
                                  ],
                                  "document_annotation": "{\\"name\\":\\"Oats\\",\\"brand\\":\\"Brand\\",\\"store_name\\":\\"Aldi\\"}"
                                }
                                """)));

        var fastProperties = new NutritionScannerProperties();
        fastProperties.setBaseUrl("http://localhost:" + wireMock.port());
        fastProperties.setApiKey("test-key");
        fastProperties.setTimeout(Duration.ofMillis(100));
        var fastTimeoutService = new NutritionScanService(new ObjectMapper(), fastProperties);

        var image1 = new MockMultipartFile("images", "front.png", "image/png", new byte[]{1});
        var image2 = new MockMultipartFile("images", "back.png", "image/png", new byte[]{2});
        var result = fastTimeoutService.scan(List.of(image1, image2));

        assertTrue(result.scanSucceeded());
        assertEquals("Oats", result.product().name());
        assertEquals("Brand", result.product().brand());
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("Read timed out")));
    }

    @Test
    void scan_multiImage_oneReturns503_returnsFailed() throws Exception {
        wireMock.stubFor(post(urlPathEqualTo("/v1/ocr"))
                .withRequestBody(containing("base64,AQ"))
                .willReturn(aResponse()
                        .withStatus(503)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"service unavailable\"}")));

        wireMock.stubFor(post(urlPathEqualTo("/v1/ocr"))
                .withRequestBody(containing("base64,Ag"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "pages": [
                                    {
                                      "index": 0,
                                      "markdown": "# Oats"
                                    }
                                  ],
                                  "document_annotation": "{\\"name\\":\\"Oats\\",\\"brand\\":\\"Brand\\",\\"store_name\\":\\"Aldi\\"}"
                                }
                                """)));

        var image1 = new MockMultipartFile("images", "front.png", "image/png", new byte[]{1});
        var image2 = new MockMultipartFile("images", "back.png", "image/png", new byte[]{2});
        var result = service.scan(List.of(image1, image2));

        assertTrue(result.scanSucceeded());
        assertEquals("Oats", result.product().name());
        assertEquals("Brand", result.product().brand());
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("503")));
    }

    @Test
    void scan_http429_returnsFailed() throws Exception {
        wireMock.stubFor(post(urlPathEqualTo("/v1/ocr"))
                .willReturn(aResponse()
                        .withStatus(429)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"too many requests\"}")));

        var image = new MockMultipartFile("images", "label.png", "image/png", new byte[]{1});
        var result = service.scan(List.of(image));

        assertFalse(result.scanSucceeded());
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("429")));
    }

    @Test
    void scan_http503_returnsFailed() throws Exception {
        wireMock.stubFor(post(urlPathEqualTo("/v1/ocr"))
                .willReturn(aResponse()
                        .withStatus(503)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"service unavailable\"}")));

        var image = new MockMultipartFile("images", "label.png", "image/png", new byte[]{1});
        var result = service.scan(List.of(image));

        assertFalse(result.scanSucceeded());
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("503")));
    }

    @Test
    void scan_multiImage_oneReturns503_otherReturnsNoUsableData_returnsFailed() throws Exception {
        wireMock.stubFor(post(urlPathEqualTo("/v1/ocr"))
                .withRequestBody(containing("base64,AQ"))
                .willReturn(aResponse()
                        .withStatus(503)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"service unavailable\"}")));

        wireMock.stubFor(post(urlPathEqualTo("/v1/ocr"))
                .withRequestBody(containing("base64,Ag"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "pages": [
                                    {
                                      "index": 0,
                                      "markdown": ""
                                    }
                                  ],
                                  "document_annotation": ""
                                }
                                """)));

        var image1 = new MockMultipartFile("images", "front.png", "image/png", new byte[]{1});
        var image2 = new MockMultipartFile("images", "back.png", "image/png", new byte[]{2});
        var result = service.scan(List.of(image1, image2));

        assertFalse(result.scanSucceeded());
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("503")));
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("Unable to extract useful OCR values")));
    }

    @Test
    void scan_multiImage_oneReturns429_otherReturns503_returnsFailed() throws Exception {
        wireMock.stubFor(post(urlPathEqualTo("/v1/ocr"))
                .withRequestBody(containing("base64,AQ"))
                .willReturn(aResponse()
                        .withStatus(429)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"too many requests\"}")));

        wireMock.stubFor(post(urlPathEqualTo("/v1/ocr"))
                .withRequestBody(containing("base64,Ag"))
                .willReturn(aResponse()
                        .withStatus(503)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"service unavailable\"}")));

        var image1 = new MockMultipartFile("images", "front.png", "image/png", new byte[]{1});
        var image2 = new MockMultipartFile("images", "back.png", "image/png", new byte[]{2});
        var result = service.scan(List.of(image1, image2));

        assertFalse(result.scanSucceeded());
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("429")));
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("503")));
    }

    @Test
    void scan_slowResponse_exceedsReadTimeout_returnsFailed() throws Exception {
        wireMock.stubFor(post(urlPathEqualTo("/v1/ocr"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{}")
                        .withFixedDelay(1000)));

        var fastProperties = new NutritionScannerProperties();
        fastProperties.setBaseUrl("http://localhost:" + wireMock.port());
        fastProperties.setApiKey("test-key");
        fastProperties.setTimeout(Duration.ofMillis(100));
        var fastTimeoutService = new NutritionScanService(new ObjectMapper(), fastProperties);

        var image = new MockMultipartFile("images", "label.png", "image/png", new byte[]{1});
        var result = fastTimeoutService.scan(List.of(image));

        assertFalse(result.scanSucceeded());
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("Read timed out")));
    }

    @Test
    void scan_multiImage_oneReturns429_otherExceedsReadTimeout_returnsFailed() throws Exception {
        wireMock.stubFor(post(urlPathEqualTo("/v1/ocr"))
                .withRequestBody(containing("base64,AQ"))
                .willReturn(aResponse()
                        .withStatus(429)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"too many requests\"}")));

        wireMock.stubFor(post(urlPathEqualTo("/v1/ocr"))
                .withRequestBody(containing("base64,Ag"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{}")
                        .withFixedDelay(1000)));

        var fastProperties = new NutritionScannerProperties();
        fastProperties.setBaseUrl("http://localhost:" + wireMock.port());
        fastProperties.setApiKey("test-key");
        fastProperties.setTimeout(Duration.ofMillis(100));
        var fastTimeoutService = new NutritionScanService(new ObjectMapper(), fastProperties);

        var image1 = new MockMultipartFile("images", "front.png", "image/png", new byte[]{1});
        var image2 = new MockMultipartFile("images", "back.png", "image/png", new byte[]{2});
        var result = fastTimeoutService.scan(List.of(image1, image2));

        assertFalse(result.scanSucceeded());
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("429")));
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("Read timed out")));
    }

    @Test
    void scan_multiImage_timeoutImageFirst_429Second_returnsFailed() throws Exception {
        wireMock.stubFor(post(urlPathEqualTo("/v1/ocr"))
                .withRequestBody(containing("base64,AQ"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{}")
                        .withFixedDelay(1000)));

        wireMock.stubFor(post(urlPathEqualTo("/v1/ocr"))
                .withRequestBody(containing("base64,Ag"))
                .willReturn(aResponse()
                        .withStatus(429)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"too many requests\"}")));

        var fastProperties = new NutritionScannerProperties();
        fastProperties.setBaseUrl("http://localhost:" + wireMock.port());
        fastProperties.setApiKey("test-key");
        fastProperties.setTimeout(Duration.ofMillis(100));
        var fastTimeoutService = new NutritionScanService(new ObjectMapper(), fastProperties);

        var image1 = new MockMultipartFile("images", "front.png", "image/png", new byte[]{1});
        var image2 = new MockMultipartFile("images", "back.png", "image/png", new byte[]{2});
        var result = fastTimeoutService.scan(List.of(image1, image2));

        assertFalse(result.scanSucceeded());
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("Read timed out")));
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("429")));
    }

    @Test
    void scan_multiImage_oneSucceedsTwoFail_preservesDataAndCollectsAllErrors() throws Exception {
        wireMock.stubFor(post(urlPathEqualTo("/v1/ocr"))
                .withRequestBody(containing("base64,AQ"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "pages": [
                                    {
                                      "index": 0,
                                      "markdown": "# Oats"
                                    }
                                  ],
                                  "document_annotation": "{\\"name\\":\\"Oats\\",\\"brand\\":\\"Brand\\",\\"store_name\\":\\"Aldi\\"}"
                                }
                                """)));

        wireMock.stubFor(post(urlPathEqualTo("/v1/ocr"))
                .withRequestBody(containing("base64,Ag"))
                .willReturn(aResponse()
                        .withStatus(400)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"bad request\"}")));

        wireMock.stubFor(post(urlPathEqualTo("/v1/ocr"))
                .withRequestBody(containing("base64,Aw"))
                .willReturn(aResponse()
                        .withStatus(503)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"service unavailable\"}")));

        var image1 = new MockMultipartFile("images", "front.png", "image/png", new byte[]{1});
        var image2 = new MockMultipartFile("images", "back.png", "image/png", new byte[]{2});
        var image3 = new MockMultipartFile("images", "side.png", "image/png", new byte[]{3});
        var result = service.scan(List.of(image1, image2, image3));

        assertTrue(result.scanSucceeded());
        assertEquals("Oats", result.product().name());
        assertEquals("Brand", result.product().brand());
        assertEquals("Aldi", result.product().storeName());
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("400")));
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("503")));
        var recorded = wireMock.findAll(postRequestedFor(urlPathEqualTo("/v1/ocr")));
        assertEquals(3, recorded.size());
    }

    @Test
    void scan_multiImage_productOnlyAnnotationAndEmptySecondImage_mergesProductFromAnnotation() throws Exception {
        wireMock.stubFor(post(urlPathEqualTo("/v1/ocr"))
                .withRequestBody(containing("base64,AQ"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "pages": [
                                    {
                                      "index": 0,
                                      "markdown": "# Oats"
                                    }
                                  ],
                                  "document_annotation": "{\\"name\\":\\"Oats\\",\\"brand\\":\\"SomeBrand\\",\\"store_name\\":\\"Tesco\\"}"
                                }
                                """)));

        wireMock.stubFor(post(urlPathEqualTo("/v1/ocr"))
                .withRequestBody(containing("base64,Ag"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "pages": [
                                    {
                                      "index": 0,
                                      "markdown": ""
                                    }
                                  ],
                                  "document_annotation": ""
                                }
                                """)));

        var image1 = new MockMultipartFile("images", "front.png", "image/png", new byte[]{1});
        var image2 = new MockMultipartFile("images", "back.png", "image/png", new byte[]{2});
        var result = service.scan(List.of(image1, image2));

        assertTrue(result.scanSucceeded());

        assertEquals("Oats", result.product().name());
        assertEquals("SomeBrand", result.product().brand());
        assertEquals("Tesco", result.product().storeName());

        assertNull(result.parsed().caloriesPer100());
        assertNull(result.parsed().caloriesPerServing());
        assertNull(result.parsed().fatPer100());
        assertNull(result.parsed().proteinPer100());
        assertNull(result.parsed().carbsPer100());

        assertEquals(ScanSource.NONE, result.sourceUsed());
        assertEquals(ScanSource.ANNOTATION, result.productSourceUsed());
        assertTrue(result.productUsedAnnotationFallback());
        assertFalse(result.usedAnnotationFallback());
        assertTrue(result.warnings().contains("Unable to extract useful OCR values"));

        var recorded = wireMock.findAll(postRequestedFor(urlPathEqualTo("/v1/ocr")));
        assertEquals(2, recorded.size());
    }

    @Test
    void scan_emptyPagesArray_withAnnotation_succeeds() throws Exception {
        wireMock.stubFor(post(urlPathEqualTo("/v1/ocr"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "pages": [],
                                  "document_annotation": "{\\"name\\":\\"Oats\\",\\"brand\\":\\"Brand\\",\\"store_name\\":\\"Aldi\\",\\"servings_per_container\\":3,\\"total_weight\\":1200,\\"total_weight_unit\\":\\"g\\",\\"serving_size\\":40,\\"serving_unit\\":\\"g\\",\\"macros_per_100\\":{\\"energy_kj\\":1494,\\"energy_kcal\\":354,\\"fat_g\\":4.1,\\"carbohydrate_g\\":61.7,\\"protein_g\\":12.2}}"
                                }
                                """)));

        var image = new MockMultipartFile("images", "label.png", "image/png", new byte[]{1});
        var result = service.scan(List.of(image));

        assertTrue(result.scanSucceeded());
        assertEquals(354f, result.parsed().caloriesPer100());
        assertEquals(40f, result.parsed().servingSize());
        assertEquals("Oats", result.product().name());
        assertEquals("Aldi", result.product().storeName());
        assertEquals(3f, result.product().servingsPerContainer());
        assertEquals(1200f, result.product().totalWeight());
        assertEquals(FoodEntity.Unit.G, result.product().totalWeightUnit());
        assertEquals(ScanSource.ANNOTATION, result.sourceUsed());
        assertEquals(ScanSource.ANNOTATION, result.productSourceUsed());
        assertFalse(result.usedAnnotationFallback());
        assertTrue(result.productUsedAnnotationFallback());
    }

    @Test
    void scan_missingPagesField_withAnnotation_succeeds() throws Exception {
        wireMock.stubFor(post(urlPathEqualTo("/v1/ocr"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "document_annotation": "{\\"name\\":\\"Oats\\",\\"brand\\":\\"Brand\\",\\"store_name\\":\\"Aldi\\",\\"servings_per_container\\":3,\\"total_weight\\":1200,\\"total_weight_unit\\":\\"g\\",\\"serving_size\\":40,\\"serving_unit\\":\\"g\\",\\"macros_per_100\\":{\\"energy_kj\\":1494,\\"energy_kcal\\":354,\\"fat_g\\":4.1,\\"carbohydrate_g\\":61.7,\\"protein_g\\":12.2}}"
                                }
                                """)));

        var image = new MockMultipartFile("images", "label.png", "image/png", new byte[]{1});
        var result = service.scan(List.of(image));

        assertTrue(result.scanSucceeded());
        assertEquals(354f, result.parsed().caloriesPer100());
        assertEquals(40f, result.parsed().servingSize());
        assertEquals("Oats", result.product().name());
        assertEquals(3f, result.product().servingsPerContainer());
        assertEquals(ScanSource.ANNOTATION, result.sourceUsed());
        assertEquals(ScanSource.ANNOTATION, result.productSourceUsed());
        assertFalse(result.usedAnnotationFallback());
        assertTrue(result.productUsedAnnotationFallback());
    }

    @Test
    void scan_pagesWithNullFirstEntry_withAnnotation_succeeds() throws Exception {
        wireMock.stubFor(post(urlPathEqualTo("/v1/ocr"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "pages": [null],
                                  "document_annotation": "{\\"name\\":\\"Oats\\",\\"brand\\":\\"Brand\\",\\"store_name\\":\\"Aldi\\",\\"servings_per_container\\":3,\\"total_weight\\":1200,\\"total_weight_unit\\":\\"g\\",\\"serving_size\\":40,\\"serving_unit\\":\\"g\\",\\"macros_per_100\\":{\\"energy_kj\\":1494,\\"energy_kcal\\":354,\\"fat_g\\":4.1,\\"carbohydrate_g\\":61.7,\\"protein_g\\":12.2}}"
                                }
                                """)));

        var image = new MockMultipartFile("images", "label.png", "image/png", new byte[]{1});
        var result = service.scan(List.of(image));

        assertTrue(result.scanSucceeded());
        assertEquals(354f, result.parsed().caloriesPer100());
        assertEquals(40f, result.parsed().servingSize());
        assertEquals("Oats", result.product().name());
        assertEquals(1200f, result.product().totalWeight());
        assertEquals(FoodEntity.Unit.G, result.product().totalWeightUnit());
        assertEquals(ScanSource.ANNOTATION, result.sourceUsed());
        assertEquals(ScanSource.ANNOTATION, result.productSourceUsed());
        assertFalse(result.usedAnnotationFallback());
        assertTrue(result.productUsedAnnotationFallback());
    }

    @Test
    void scan_textPlainContentType_succeeds() throws Exception {
        wireMock.stubFor(post(urlPathEqualTo("/v1/ocr"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/plain")
                        .withBody("""
                                {
                                  "pages": [
                                    {
                                      "index": 0,
                                      "markdown": "# Oats"
                                    }
                                  ],
                                  "document_annotation": "{\\"name\\":\\"Oats\\",\\"brand\\":\\"Brand\\",\\"store_name\\":\\"Aldi\\",\\"servings_per_container\\":3,\\"total_weight\\":1200,\\"total_weight_unit\\":\\"g\\",\\"serving_size\\":40,\\"serving_unit\\":\\"g\\",\\"macros_per_serving\\":{\\"energy_kcal\\":142,\\"fat_g\\":1.7,\\"carbohydrate_g\\":24.7,\\"protein_g\\":4.9},\\"macros_per_100\\":{\\"energy_kj\\":1494,\\"energy_kcal\\":354,\\"fat_g\\":4.1,\\"carbohydrate_g\\":61.7,\\"protein_g\\":12.2}}"
                                }
                                """)));

        var image = new MockMultipartFile("images", "label.png", "image/png", new byte[]{1});
        var result = service.scan(List.of(image));

        assertTrue(result.scanSucceeded());
        assertEquals(40f, result.parsed().servingSize());
        assertEquals(FoodEntity.Unit.G, result.parsed().servingUnit());
        assertEquals(142f, result.parsed().caloriesPerServing());
        assertEquals(354f, result.parsed().caloriesPer100());
        assertEquals("Oats", result.product().name());
        assertEquals("Brand", result.product().brand());
        assertEquals("Aldi", result.product().storeName());
        assertEquals(3f, result.product().servingsPerContainer());
        assertEquals(1200f, result.product().totalWeight());
        assertEquals(FoodEntity.Unit.G, result.product().totalWeightUnit());
        assertEquals(ScanSource.ANNOTATION, result.sourceUsed());
        assertFalse(result.usedAnnotationFallback());

        var recorded = wireMock.findAll(postRequestedFor(urlPathEqualTo("/v1/ocr")));
        assertEquals(1, recorded.size());
    }

    @Test
    void scan_missingContentType_succeeds() throws Exception {
        wireMock.stubFor(post(urlPathEqualTo("/v1/ocr"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody("""
                                {
                                  "pages": [
                                    {
                                      "index": 0,
                                      "markdown": "# Oats"
                                    }
                                  ],
                                  "document_annotation": "{\\"name\\":\\"Oats\\",\\"brand\\":\\"Brand\\",\\"store_name\\":\\"Aldi\\",\\"servings_per_container\\":3,\\"total_weight\\":1200,\\"total_weight_unit\\":\\"g\\",\\"serving_size\\":40,\\"serving_unit\\":\\"g\\",\\"macros_per_serving\\":{\\"energy_kcal\\":142,\\"fat_g\\":1.7,\\"carbohydrate_g\\":24.7,\\"protein_g\\":4.9},\\"macros_per_100\\":{\\"energy_kj\\":1494,\\"energy_kcal\\":354,\\"fat_g\\":4.1,\\"carbohydrate_g\\":61.7,\\"protein_g\\":12.2}}"
                                }
                                """)));

        var image = new MockMultipartFile("images", "label.png", "image/png", new byte[]{1});
        var result = service.scan(List.of(image));

        assertTrue(result.scanSucceeded());
        assertEquals(40f, result.parsed().servingSize());
        assertEquals(FoodEntity.Unit.G, result.parsed().servingUnit());
        assertEquals(142f, result.parsed().caloriesPerServing());
        assertEquals(354f, result.parsed().caloriesPer100());
        assertEquals("Oats", result.product().name());
        assertEquals("Brand", result.product().brand());
        assertEquals("Aldi", result.product().storeName());
        assertEquals(3f, result.product().servingsPerContainer());
        assertEquals(1200f, result.product().totalWeight());
        assertEquals(FoodEntity.Unit.G, result.product().totalWeightUnit());
        assertEquals(ScanSource.ANNOTATION, result.sourceUsed());
        assertFalse(result.usedAnnotationFallback());

        var recorded = wireMock.findAll(postRequestedFor(urlPathEqualTo("/v1/ocr")));
        assertEquals(1, recorded.size());
    }

}

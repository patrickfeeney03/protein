package com.example.demo.services;

import com.example.demo.NutritionScannerProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.jupiter.api.Assertions.*;

class OcrApiClientContractTest {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(options().dynamicPort())
            .build();

    private NutritionScannerProperties properties;
    private OcrApiClient client;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        properties = new NutritionScannerProperties();
        properties.setBaseUrl("http://localhost:" + wireMock.getPort());
        properties.setApiKey("contract-test-key");
        properties.setModel("mistral-ocr-latest");
        client = new OcrApiClient(properties);
    }

    @Nested
    class RequestContract {

        @Test
        void sendsPostToV1Ocr() throws Exception {
            wireMock.stubFor(post(urlPathEqualTo("/v1/ocr"))
                    .willReturn(aResponse().withStatus(200).withBody("{}")));

            client.ocrRequest(validImage());

            wireMock.verify(postRequestedFor(urlPathEqualTo("/v1/ocr")));
        }

        @Test
        void sendsBearerAuthHeader() throws Exception {
            wireMock.stubFor(post(urlPathEqualTo("/v1/ocr"))
                    .willReturn(aResponse().withStatus(200).withBody("{}")));

            client.ocrRequest(validImage());

            wireMock.verify(postRequestedFor(urlPathEqualTo("/v1/ocr"))
                    .withHeader("Authorization", equalTo("Bearer contract-test-key")));
        }

        @Test
        void sendsJsonContentType() throws Exception {
            wireMock.stubFor(post(urlPathEqualTo("/v1/ocr"))
                    .willReturn(aResponse().withStatus(200).withBody("{}")));

            client.ocrRequest(validImage());

            wireMock.verify(postRequestedFor(urlPathEqualTo("/v1/ocr"))
                    .withHeader("Content-Type", containing("application/json")));
        }

        @Test
        void requestBodyHasModel() throws Exception {
            wireMock.stubFor(post(urlPathEqualTo("/v1/ocr"))
                    .willReturn(aResponse().withStatus(200).withBody("{}")));

            client.ocrRequest(validImage());

            var body = capturedRequestBody();
            assertEquals("mistral-ocr-latest", body.get("model").asText());
        }

        @Test
        void requestBodyHasTableFormatHtml() throws Exception {
            wireMock.stubFor(post(urlPathEqualTo("/v1/ocr"))
                    .willReturn(aResponse().withStatus(200).withBody("{}")));

            client.ocrRequest(validImage());

            var body = capturedRequestBody();
            assertEquals("html", body.get("table_format").asText());
        }

        @Test
        void requestBodyDisablesIncludeImageBase64() throws Exception {
            wireMock.stubFor(post(urlPathEqualTo("/v1/ocr"))
                    .willReturn(aResponse().withStatus(200).withBody("{}")));

            client.ocrRequest(validImage());

            var body = capturedRequestBody();
            assertFalse(body.get("include_image_base64").asBoolean());
        }

        @Test
        void requestBodyHasDocumentWithImageUrlType() throws Exception {
            wireMock.stubFor(post(urlPathEqualTo("/v1/ocr"))
                    .willReturn(aResponse().withStatus(200).withBody("{}")));

            client.ocrRequest(validImage());

            var body = capturedRequestBody();
            var document = body.get("document");
            assertEquals("image_url", document.get("type").asText());
            assertTrue(document.get("image_url").asText().startsWith("data:image/png;base64,"));
        }

        @Test
        void requestBodyHasNutritionPrompt() throws Exception {
            wireMock.stubFor(post(urlPathEqualTo("/v1/ocr"))
                    .willReturn(aResponse().withStatus(200).withBody("{}")));

            client.ocrRequest(validImage());

            var body = capturedRequestBody();
            var prompt = body.get("document_annotation_prompt").asText();
            assertTrue(prompt.contains("Extract nutrition"));
            assertTrue(prompt.contains("Per 100g"));
            assertTrue(prompt.contains("Return numbers only without units"));
        }

        @Test
        void requestBodyHasJsonSchemaAnnotationFormat() throws Exception {
            wireMock.stubFor(post(urlPathEqualTo("/v1/ocr"))
                    .willReturn(aResponse().withStatus(200).withBody("{}")));

            client.ocrRequest(validImage());

            var body = capturedRequestBody();
            var format = body.get("document_annotation_format");
            assertEquals("json_schema", format.get("type").asText());
            var jsonSchema = format.get("json_schema");
            assertEquals("nutrition_scan", jsonSchema.get("name").asText());
        }

        @Test
        void requestBodyAnnotationSchemaHasMacrosPer100() throws Exception {
            wireMock.stubFor(post(urlPathEqualTo("/v1/ocr"))
                    .willReturn(aResponse().withStatus(200).withBody("{}")));

            client.ocrRequest(validImage());

            var schema = capturedAnnotationSchema();
            assertTrue(schema.get("properties").has("macros_per_100"));
            var macrosPer100 = schema.get("properties").get("macros_per_100");
            assertEquals("object", macrosPer100.get("type").asText());
            var required = macrosPer100.get("required");
            assertTrue(required.toString().contains("energy_kcal"));
            assertTrue(required.toString().contains("fat_g"));
            assertTrue(required.toString().contains("carbohydrate_g"));
            assertTrue(required.toString().contains("protein_g"));
        }

        @Test
        void requestBodyAnnotationSchemaHasMacrosPerServing() throws Exception {
            wireMock.stubFor(post(urlPathEqualTo("/v1/ocr"))
                    .willReturn(aResponse().withStatus(200).withBody("{}")));

            client.ocrRequest(validImage());

            var schema = capturedAnnotationSchema();
            assertTrue(schema.get("properties").has("macros_per_serving"));
            var macrosPerServing = schema.get("properties").get("macros_per_serving");
            assertEquals("object", macrosPerServing.get("type").get(0).asText());
            assertEquals("null", macrosPerServing.get("type").get(1).asText());
        }

        @Test
        void requestBodyAnnotationSchemaHasNameBrandBarcode() throws Exception {
            wireMock.stubFor(post(urlPathEqualTo("/v1/ocr"))
                    .willReturn(aResponse().withStatus(200).withBody("{}")));

            client.ocrRequest(validImage());

            var schema = capturedAnnotationSchema();
            var properties = schema.get("properties");
            assertTrue(properties.has("name"));
            assertTrue(properties.has("brand"));
            assertTrue(properties.has("barcode_number"));
        }

        @Test
        void sendsImageAsBase64DataUrl() throws Exception {
            wireMock.stubFor(post(urlPathEqualTo("/v1/ocr"))
                    .willReturn(aResponse().withStatus(200).withBody("{}")));

            client.ocrRequest(validImage());

            var body = capturedRequestBody();
            var imageUrl = body.get("document").get("image_url").asText();
            assertTrue(imageUrl.startsWith("data:image/png;base64,"));
            assertTrue(imageUrl.length() > "data:image/png;base64,".length());
        }

        @Test
        void usesConfiguredBaseUrl() throws Exception {
            properties.setBaseUrl("http://localhost:" + wireMock.getPort() + "/custom-prefix");
            client = new OcrApiClient(properties);
            wireMock.stubFor(post(urlPathEqualTo("/custom-prefix/v1/ocr"))
                    .willReturn(aResponse().withStatus(200).withBody("{}")));

            client.ocrRequest(validImage());

            wireMock.verify(postRequestedFor(urlPathEqualTo("/custom-prefix/v1/ocr")));
        }
    }

    @Nested
    class ResponseContract {

        @Test
        void returnsBodyOn2xx() throws Exception {
            wireMock.stubFor(post(urlPathEqualTo("/v1/ocr"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("{\"pages\":[{\"index\":0,\"markdown\":\"# Oats\"}]}")));

            var result = client.ocrRequest(validImage());

            assertEquals("{\"pages\":[{\"index\":0,\"markdown\":\"# Oats\"}]}", result);
        }

        @Test
        void throwsOn400() {
            wireMock.stubFor(post(urlPathEqualTo("/v1/ocr"))
                    .willReturn(aResponse().withStatus(400).withBody("bad request")));

            var ex = assertThrows(IOException.class, () -> client.ocrRequest(validImage()));
            assertTrue(ex.getMessage().contains("400"));
        }

        @Test
        void throwsOn401() {
            wireMock.stubFor(post(urlPathEqualTo("/v1/ocr"))
                    .willReturn(aResponse().withStatus(401).withBody("unauthorized")));

            var ex = assertThrows(IOException.class, () -> client.ocrRequest(validImage()));
            assertTrue(ex.getMessage().contains("401"));
        }

        @Test
        void throwsOn403() {
            wireMock.stubFor(post(urlPathEqualTo("/v1/ocr"))
                    .willReturn(aResponse().withStatus(403).withBody("forbidden")));

            var ex = assertThrows(IOException.class, () -> client.ocrRequest(validImage()));
            assertTrue(ex.getMessage().contains("403"));
        }

        @Test
        void throwsOn500() {
            wireMock.stubFor(post(urlPathEqualTo("/v1/ocr"))
                    .willReturn(aResponse().withStatus(500).withBody("server error")));

            var ex = assertThrows(IOException.class, () -> client.ocrRequest(validImage()));
            assertTrue(ex.getMessage().contains("500"));
        }

        @Test
        void throwsOn502() {
            wireMock.stubFor(post(urlPathEqualTo("/v1/ocr"))
                    .willReturn(aResponse().withStatus(502).withBody("bad gateway")));

            var ex = assertThrows(IOException.class, () -> client.ocrRequest(validImage()));
            assertTrue(ex.getMessage().contains("502"));
        }

        @Test
        void throwsOnNullBody() {
            wireMock.stubFor(post(urlPathEqualTo("/v1/ocr"))
                    .willReturn(aResponse().withStatus(200)));

            var ex = assertThrows(IOException.class, () -> client.ocrRequest(validImage()));
            assertTrue(ex.getMessage().contains("empty response"));
        }

        @Test
        void throwsOnBlankBody() {
            wireMock.stubFor(post(urlPathEqualTo("/v1/ocr"))
                    .willReturn(aResponse().withStatus(200).withBody("   ")));

            var ex = assertThrows(IOException.class, () -> client.ocrRequest(validImage()));
            assertTrue(ex.getMessage().contains("empty response"));
        }

        @Test
        void throwsOnWhitespaceOnlyBody() {
            wireMock.stubFor(post(urlPathEqualTo("/v1/ocr"))
                    .willReturn(aResponse().withStatus(200).withBody("\t\n  \n")));

            var ex = assertThrows(IOException.class, () -> client.ocrRequest(validImage()));
            assertTrue(ex.getMessage().contains("empty response"));
        }

        @Test
        void returnsBodyWithMinimalJson() throws Exception {
            wireMock.stubFor(post(urlPathEqualTo("/v1/ocr"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("{}")));

            var result = client.ocrRequest(validImage());

            assertEquals("{}", result);
        }

        @Test
        void returnsBodyWithRealisticMistralResponse() throws Exception {
            wireMock.stubFor(post(urlPathEqualTo("/v1/ocr"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                    {
                                      "pages": [
                                        {
                                          "index": 0,
                                          "markdown": "# Oats\\n\\n**Nutritional Information**\\n\\n| Typical Values | Per 100g |\\n|----------------|----------|\\n| Energy         | 354kcal  |\\n| Fat            | 4.1g     |\\n",
                                          "tables": [
                                            {
                                              "id": "tbl-0.html",
                                              "content": "<table><tr><th>Typical Values</th><th>Per 100g</th></tr></table>"
                                            }
                                          ]
                                        }
                                      ]
                                    }""")));

            var result = client.ocrRequest(validImage());

            assertTrue(result.contains("pages"));
            assertTrue(result.contains("markdown"));
            assertTrue(result.contains("tables"));
        }

        @Test
        void throwsOn429() {
            wireMock.stubFor(post(urlPathEqualTo("/v1/ocr"))
                    .willReturn(aResponse().withStatus(429).withBody("rate limited")));

            var ex = assertThrows(IOException.class, () -> client.ocrRequest(validImage()));
            assertTrue(ex.getMessage().contains("429"));
        }

        @Test
        void throwsOn503() {
            wireMock.stubFor(post(urlPathEqualTo("/v1/ocr"))
                    .willReturn(aResponse().withStatus(503).withBody("service unavailable")));

            var ex = assertThrows(IOException.class, () -> client.ocrRequest(validImage()));
            assertTrue(ex.getMessage().contains("503"));
        }

        @Test
        void slowResponse_exceedsReadTimeout_throwsIOException() {
            properties.setTimeout(Duration.ofMillis(100));
            client = new OcrApiClient(properties);
            wireMock.stubFor(post(urlPathEqualTo("/v1/ocr"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withBody("{}")
                            .withFixedDelay(1000)));

            var ex = assertThrows(IOException.class, () -> client.ocrRequest(validImage()));
            assertTrue(ex.getMessage().contains("timed out")
                            || ex.getMessage().contains("timeout")
                            || ex.getMessage().contains("Timeout")
                            || ex.getMessage().contains("Read timed"),
                    "Expected timeout-related message, got: " + ex.getMessage());
        }
    }

    private com.fasterxml.jackson.databind.JsonNode capturedRequestBody() {
        var requests = wireMock.findAll(postRequestedFor(urlPathEqualTo("/v1/ocr")));
        try {
            return objectMapper.readTree(requests.getFirst().getBodyAsString());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private com.fasterxml.jackson.databind.JsonNode capturedAnnotationSchema() {
        var body = capturedRequestBody();
        return body.get("document_annotation_format")
                .get("json_schema")
                .get("schema");
    }

    private static MockMultipartFile validImage() {
        return new MockMultipartFile("images", "label.png", "image/png", new byte[]{1, 2, 3});
    }
}

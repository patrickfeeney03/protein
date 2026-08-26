package com.example.demo.services;

import com.example.demo.NutritionScannerProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.client.RestTemplate;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OcrApiClientTest {

    private final RestTemplate restTemplate = mock(RestTemplate.class);
    private NutritionScannerProperties properties;
    private OcrApiClient client;

    @BeforeEach
    void setUp() {
        properties = new NutritionScannerProperties();
        properties.setBaseUrl("https://api.test.com");
        properties.setApiKey("test-key-123");
        properties.setModel("test-model");
        client = new OcrApiClient(restTemplate, properties);
    }

    @Test
    void ocrRequest_success_returnsResponseBody() throws Exception {
        var image = new MockMultipartFile("image", "test.png", "image/png", new byte[]{1, 2, 3});
        when(restTemplate.postForEntity(
                eq("https://api.test.com/v1/ocr"),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(ResponseEntity.ok("{\"result\":\"ok\"}"));

        var result = client.ocrRequest(image);

        assertEquals("{\"result\":\"ok\"}", result);
    }

    @Test
    void ocrRequest_non2xxResponse_throwsIOException() {
        var image = new MockMultipartFile("image", "test.png", "image/png", new byte[]{1});
        when(restTemplate.postForEntity(
                eq("https://api.test.com/v1/ocr"),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(ResponseEntity.status(HttpStatus.BAD_REQUEST).body("error"));

        var ex = assertThrows(IOException.class, () -> client.ocrRequest(image));
        assertTrue(ex.getMessage().contains("400"));
    }

    @Test
    void ocrRequest_nullBody_throwsIOException() {
        var image = new MockMultipartFile("image", "test.png", "image/png", new byte[]{1});
        when(restTemplate.postForEntity(
                eq("https://api.test.com/v1/ocr"),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(ResponseEntity.ok(null));

        var ex = assertThrows(IOException.class, () -> client.ocrRequest(image));
        assertTrue(ex.getMessage().contains("empty response"));
    }

    @Test
    void ocrRequest_blankBody_throwsIOException() {
        var image = new MockMultipartFile("image", "test.png", "image/png", new byte[]{1});
        when(restTemplate.postForEntity(
                eq("https://api.test.com/v1/ocr"),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(ResponseEntity.ok("   "));

        var ex = assertThrows(IOException.class, () -> client.ocrRequest(image));
        assertTrue(ex.getMessage().contains("empty response"));
    }

    @Test
    void ocrRequest_nullContentType_throwsIllegalArgument() {
        var image = new MockMultipartFile("image", "test.png", null, new byte[]{1});

        assertThrows(IllegalArgumentException.class, () -> client.ocrRequest(image));
    }

    @Test
    void ocrRequest_blankContentType_throwsIllegalArgument() {
        var image = new MockMultipartFile("image", "test.png", " ", new byte[]{1});

        assertThrows(IllegalArgumentException.class, () -> client.ocrRequest(image));
    }

    @Test
    void ocrRequest_sendsCorrectPayloadStructure() throws Exception {
        var image = new MockMultipartFile("image", "test.png", "image/png", new byte[]{1, 2, 3});
        when(restTemplate.postForEntity(
                eq("https://api.test.com/v1/ocr"),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(ResponseEntity.ok("{}"));

        client.ocrRequest(image);

        var captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(eq("https://api.test.com/v1/ocr"), captor.capture(), eq(String.class));

        var body = extractBody(captor);
        assertEquals("test-model", body.get("model"));
        assertEquals("html", body.get("table_format"));
        assertEquals(false, body.get("include_image_base64"));
        assertTrue(((String) body.get("document_annotation_prompt")).contains("Extract nutrition"));
    }

    @Test
    void ocrRequest_sendsCorrectDocumentStructure() throws Exception {
        var image = new MockMultipartFile("image", "test.png", "image/png", new byte[]{1, 2, 3});
        when(restTemplate.postForEntity(
                eq("https://api.test.com/v1/ocr"),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(ResponseEntity.ok("{}"));

        client.ocrRequest(image);

        var captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(eq("https://api.test.com/v1/ocr"), captor.capture(), eq(String.class));

        var body = extractBody(captor);
        var document = (Map<String, Object>) body.get("document");
        assertNotNull(document);
        assertEquals("image_url", document.get("type"));
        var imageUrl = (String) document.get("image_url");
        assertNotNull(imageUrl);
        assertTrue(imageUrl.startsWith("data:image/png;base64,"));
        assertTrue(imageUrl.contains("AQID"));
    }

    @Test
    void ocrRequest_sendsAnnotationFormat() throws Exception {
        var image = new MockMultipartFile("image", "test.png", "image/png", new byte[]{1});
        when(restTemplate.postForEntity(
                eq("https://api.test.com/v1/ocr"),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(ResponseEntity.ok("{}"));

        client.ocrRequest(image);

        var captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(eq("https://api.test.com/v1/ocr"), captor.capture(), eq(String.class));

        var body = extractBody(captor);
        var annotationFormat = (Map<String, Object>) body.get("document_annotation_format");
        assertNotNull(annotationFormat);
        assertEquals("json_schema", annotationFormat.get("type"));
        var jsonSchema = (Map<String, Object>) annotationFormat.get("json_schema");
        assertNotNull(jsonSchema);
        assertEquals("nutrition_scan", jsonSchema.get("name"));
    }

    @Test
    void ocrRequest_schemaHasCorrectProperties() throws Exception {
        var image = new MockMultipartFile("image", "test.png", "image/png", new byte[]{1});
        when(restTemplate.postForEntity(
                eq("https://api.test.com/v1/ocr"),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(ResponseEntity.ok("{}"));

        client.ocrRequest(image);

        var captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(eq("https://api.test.com/v1/ocr"), captor.capture(), eq(String.class));

        var schema = extractSchema(captor);
        assertEquals("object", schema.get("type"));
        assertEquals(false, schema.get("additionalProperties"));
        var properties = (Map<String, Object>) schema.get("properties");
        assertNotNull(properties);
        assertTrue(properties.containsKey("name"));
        assertTrue(properties.containsKey("brand"));
        assertTrue(properties.containsKey("barcode_number"));
        assertTrue(properties.containsKey("macros_per_100"));
        assertTrue(properties.containsKey("macros_per_serving"));
    }

    @Test
    void ocrRequest_macrosPer100HasRequiredFields() throws Exception {
        var image = new MockMultipartFile("image", "test.png", "image/png", new byte[]{1});
        when(restTemplate.postForEntity(
                eq("https://api.test.com/v1/ocr"),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(ResponseEntity.ok("{}"));

        client.ocrRequest(image);

        var captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(eq("https://api.test.com/v1/ocr"), captor.capture(), eq(String.class));

        var schema = extractSchema(captor);
        var macrosPer100 = (Map<String, Object>) ((Map<String, Object>) schema.get("properties")).get("macros_per_100");
        assertNotNull(macrosPer100);
        assertEquals("object", macrosPer100.get("type"));
        var required = (List<String>) macrosPer100.get("required");
        assertTrue(required.contains("energy_kcal"));
        assertTrue(required.contains("fat_g"));
        assertTrue(required.contains("carbohydrate_g"));
        assertTrue(required.contains("protein_g"));
    }

    @Test
    void ocrRequest_setsBearerAuthAndContentType() throws Exception {
        var image = new MockMultipartFile("image", "test.png", "image/png", new byte[]{1});
        when(restTemplate.postForEntity(
                eq("https://api.test.com/v1/ocr"),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(ResponseEntity.ok("{}"));

        client.ocrRequest(image);

        var captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(eq("https://api.test.com/v1/ocr"), captor.capture(), eq(String.class));

        var headers = ((HttpEntity<?>) captor.getValue()).getHeaders();
        assertEquals(MediaType.APPLICATION_JSON, headers.getContentType());
        var auth = headers.get(HttpHeaders.AUTHORIZATION);
        assertNotNull(auth);
        assertTrue(auth.getFirst().contains("Bearer test-key-123"));
    }

    @Test
    void ocrRequest_macrosPerServingIsNullable() throws Exception {
        var image = new MockMultipartFile("image", "test.png", "image/png", new byte[]{1});
        when(restTemplate.postForEntity(
                eq("https://api.test.com/v1/ocr"),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(ResponseEntity.ok("{}"));

        client.ocrRequest(image);

        var captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(eq("https://api.test.com/v1/ocr"), captor.capture(), eq(String.class));

        var schema = extractSchema(captor);
        var macrosPerServing = (Map<String, Object>) ((Map<String, Object>) schema.get("properties")).get("macros_per_serving");
        assertNotNull(macrosPerServing);
        assertEquals(List.of("object", "null"), macrosPerServing.get("type"));
    }

    @Test
    void ocrRequest_nullableFieldsHaveCorrectType() throws Exception {
        var image = new MockMultipartFile("image", "test.png", "image/png", new byte[]{1});
        when(restTemplate.postForEntity(
                eq("https://api.test.com/v1/ocr"),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(ResponseEntity.ok("{}"));

        client.ocrRequest(image);

        var captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(eq("https://api.test.com/v1/ocr"), captor.capture(), eq(String.class));

        var schema = extractSchema(captor);
        var properties = (Map<String, Object>) schema.get("properties");
        var nameSchema = (Map<String, Object>) properties.get("name");
        assertEquals(List.of("string", "null"), nameSchema.get("type"));

        var macrosPer100 = (Map<String, Object>) properties.get("macros_per_100");
        var macroProps = (Map<String, Object>) macrosPer100.get("properties");
        var energySchema = (Map<String, Object>) macroProps.get("energy_kcal");
        assertEquals(List.of("number", "null"), energySchema.get("type"));
    }

    @Test
    void ocrRequest_usesPropertiesBaseUrl() throws Exception {
        properties.setBaseUrl("https://custom.api.example.com");
        var image = new MockMultipartFile("image", "test.png", "image/png", new byte[]{1});
        when(restTemplate.postForEntity(
                eq("https://custom.api.example.com/v1/ocr"),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(ResponseEntity.ok("{}"));

        client.ocrRequest(image);
        verify(restTemplate).postForEntity(eq("https://custom.api.example.com/v1/ocr"), any(HttpEntity.class), eq(String.class));
    }

    @Test
    void ocrRequest_non2xxWithDifferentStatus_includesStatusCode() {
        var image = new MockMultipartFile("image", "test.png", "image/png", new byte[]{1});
        when(restTemplate.postForEntity(
                eq("https://api.test.com/v1/ocr"),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("err"));

        var ex = assertThrows(IOException.class, () -> client.ocrRequest(image));
        assertTrue(ex.getMessage().contains("500"));
    }

    @Test
    void ocrRequest_toDataUrl_encodesCorrectly() throws Exception {
        var image = new MockMultipartFile("image", "test.png", "image/jpeg", new byte[]{10, 20, 30, 40});
        when(restTemplate.postForEntity(
                eq("https://api.test.com/v1/ocr"),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(ResponseEntity.ok("{}"));

        client.ocrRequest(image);

        var captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(eq("https://api.test.com/v1/ocr"), captor.capture(), eq(String.class));

        var body = extractBody(captor);
        var document = (Map<String, Object>) body.get("document");
        var imageUrl = (String) document.get("image_url");
        assertTrue(imageUrl.startsWith("data:image/jpeg;base64,"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractBody(ArgumentCaptor<?> captor) {
        return (Map<String, Object>) ((HttpEntity<?>) captor.getValue()).getBody();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractSchema(ArgumentCaptor<?> captor) {
        var body = extractBody(captor);
        var annotationFormat = (Map<String, Object>) body.get("document_annotation_format");
        var jsonSchema = (Map<String, Object>) annotationFormat.get("json_schema");
        return (Map<String, Object>) jsonSchema.get("schema");
    }
}

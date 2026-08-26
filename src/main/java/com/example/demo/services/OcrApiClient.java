package com.example.demo.services;

import com.example.demo.NutritionScannerProperties;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class OcrApiClient {

    private final RestTemplate restTemplate;
    private final NutritionScannerProperties properties;

    public OcrApiClient(NutritionScannerProperties properties) {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) properties.getTimeout().toMillis());
        requestFactory.setReadTimeout((int) properties.getTimeout().toMillis());
        this.restTemplate = new RestTemplate(requestFactory);
        this.properties = properties;
    }

    OcrApiClient(RestTemplate restTemplate, NutritionScannerProperties properties) {
        this.restTemplate = restTemplate;
        this.properties = properties;
    }

    public String ocrRequest(MultipartFile image) throws IOException {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(properties.getApiKey());

        var requestBody = buildPayload(image);
        var entity = new HttpEntity<>(requestBody, headers);
        var url = properties.getBaseUrl() + "/v1/ocr";

        ResponseEntity<String> responseEntity;
        try {
            responseEntity = restTemplate.postForEntity(url, entity, String.class);
        } catch (RestClientResponseException e) {
            throw new IOException("Mistral OCR was not 2xx successful. Code: " + e.getStatusCode().value());
        } catch (ResourceAccessException e) {
            throw new IOException("Mistral OCR request failed: " + e.getMessage(), e);
        }

        if (!responseEntity.getStatusCode().is2xxSuccessful()) {
            throw new IOException("Mistral OCR was not 2xx successful. Code: " + responseEntity.getStatusCode().value());
        }
        var responseBody = responseEntity.getBody();
        if (responseBody == null || responseBody.isBlank()) {
            throw new IOException("Mistral OCR returned an empty response with 2xx");
        }
        if (responseBody.length() > properties.getMaxResponseBytes()) {
            throw new IOException("Mistral OCR response exceeded the maximum allowed size");
        }

        return responseBody;
    }

    private Map<String, Object> buildPayload(MultipartFile image) throws IOException {
        var prompt = "Extract nutrition values from the label. Prefer the Per 100g or Per 100ml section or column. " +
                "Return numbers only without units. If a field is missing, return null.";

        var payload = new LinkedHashMap<String, Object>();
        payload.put("model", properties.getModel());
        payload.put("document", Map.of(
                "type", "image_url",
                "image_url", toDataUrl(image, requireContentType(image))
        ));
        payload.put("table_format", "html");
        payload.put("include_image_base64", false);
        payload.put("document_annotation_prompt", prompt);
        payload.put("document_annotation_format", Map.of(
                "type", "json_schema",
                "json_schema", Map.of(
                        "name", "nutrition_scan",
                        "schema", annotationSchema()
                )
        ));
        return payload;
    }

    private Map<String, Object> annotationSchema() {
        return Map.of(
                "type", "object",
                "additionalProperties", false,
                "properties", Map.ofEntries(
                        Map.entry("name", nullableStringSchema("Product name")),
                        Map.entry("brand", nullableStringSchema("Brand name")),
                        Map.entry("barcode_number", nullableStringSchema("Barcode if visible")),
                        Map.entry("macros_per_100", Map.of(
                                "type", "object",
                                "additionalProperties", false,
                                "required", List.of("energy_kcal", "fat_g", "carbohydrate_g", "protein_g"),
                                "properties", Map.of(
                                        "energy_kcal", nullableNumberSchema("Energy in kcal per 100g or 100ml"),
                                        "fat_g", nullableNumberSchema("Fat in grams per 100g or 100ml"),
                                        "carbohydrate_g", nullableNumberSchema("Carbohydrate in grams per 100g or 100ml"),
                                        "protein_g", nullableNumberSchema("Protein in grams per 100g or 100ml")
                                )
                        )),
                        Map.entry("macros_per_serving", Map.of(
                                "type", List.of("object", "null"),
                                "additionalProperties", false,
                                "required", List.of("energy_kcal", "fat_g", "carbohydrate_g", "protein_g"),
                                "properties", Map.of(
                                        "energy_kcal", nullableNumberSchema("Energy in kcal per serving"),
                                        "fat_g", nullableNumberSchema("Fat in grams per serving"),
                                        "carbohydrate_g", nullableNumberSchema("Carbohydrate in grams per serving"),
                                        "protein_g", nullableNumberSchema("Protein in grams per serving")
                                )
                        ))
                )
        );
    }

    private Map<String, Object> nullableNumberSchema(String description) {
        return Map.of(
                "type", List.of("number", "null"),
                "description", description
        );
    }

    private Map<String, Object> nullableStringSchema(String description) {
        return Map.of(
                "type", List.of("string", "null"),
                "description", description
        );
    }

    private String toDataUrl(MultipartFile file, String contentType) throws IOException {
        if (file.getSize() > properties.getMaxImageBytes()) {
            throw new IOException("Image exceeds the maximum allowed size");
        }
        var base64 = Base64.getEncoder().encodeToString(file.getBytes());
        return "data:" + contentType + ";base64," + base64;
    }

    private String requireContentType(MultipartFile file) {
        var contentType = file.getContentType();
        if (contentType == null || contentType.isBlank()) {
            throw new IllegalArgumentException("Only image uploads are supported");
        }
        var normalized = contentType.trim().toLowerCase(java.util.Locale.ROOT);
        if (!java.util.Set.of("image/jpeg", "image/png", "image/webp").contains(normalized)) {
            throw new IllegalArgumentException("Only JPEG, PNG, and WebP uploads are supported");
        }
        return normalized;
    }
}

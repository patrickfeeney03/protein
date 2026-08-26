package com.example.demo;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "app.nutrition-scanner")
public class NutritionScannerProperties {
    private String baseUrl = "https://api.mistral.ai";
    private Duration timeout = Duration.ofSeconds(30);
    private String apiKey;
    private String model = "mistral-ocr-latest";
    private long maxImageBytes = 10 * 1024 * 1024;
    private int maxImages = 3;
    private boolean validateImageSignature;
    private long maxResponseBytes = 5 * 1024 * 1024;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public long getMaxImageBytes() {
        return maxImageBytes;
    }

    public void setMaxImageBytes(long maxImageBytes) {
        this.maxImageBytes = maxImageBytes;
    }

    public int getMaxImages() {
        return maxImages;
    }

    public void setMaxImages(int maxImages) {
        this.maxImages = maxImages;
    }

    public boolean isValidateImageSignature() {
        return validateImageSignature;
    }

    public void setValidateImageSignature(boolean validateImageSignature) {
        this.validateImageSignature = validateImageSignature;
    }

    public long getMaxResponseBytes() {
        return maxResponseBytes;
    }

    public void setMaxResponseBytes(long maxResponseBytes) {
        this.maxResponseBytes = maxResponseBytes;
    }
}

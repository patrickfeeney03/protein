package com.example.demo;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "app.rate-limit")
public class RateLimitProperties {
    private Rule foodCreate = new Rule();
    private Rule foodCreateCooldown = new Rule(1, Duration.ofSeconds(20));
    private Rule commentCreate = new Rule();
    private Rule commentCreateCooldown = new Rule(1, Duration.ofSeconds(4));
    private Rule authPost = new Rule(20, Duration.ofMinutes(15));

    private Rule authPostCooldown = new Rule(1, Duration.ofSeconds(5));
    private Rule scanImage = new Rule(10, Duration.ofHours(1));
    private Rule scanImageCooldown = new Rule(1, Duration.ofSeconds(10));
    private Rule scanImageDaily = new Rule(25, Duration.ofDays(1));

    /**
     * Optional edge-provided client IP header (for example "X-Client-IP").
     * In production it is trusted only after OriginVerificationFilter has
     * authenticated the edge-to-origin shared secret, or when the connecting
     * address matches trustedProxyCidrs. Default is blank (use remote address
     * directly).
     */
    private String forwardedHeader = "";
    private boolean requireTrustedProxy;
    private String trustedProxyCidrs = "";
    private int scanImageMaxConcurrency = 1;
    private int scanImageGlobalMaxConcurrency = 4;

    public Rule getFoodCreate() {
        return foodCreate;
    }

    public void setFoodCreate(Rule foodCreate) {
        this.foodCreate = foodCreate;
    }

    public Rule getCommentCreate() {
        return commentCreate;
    }

    public void setCommentCreate(Rule commentCreate) {
        this.commentCreate = commentCreate;
    }

    public Rule getFoodCreateCooldown() {
        return foodCreateCooldown;
    }

    public void setFoodCreateCooldown(Rule foodCreateCooldown) {
        this.foodCreateCooldown = foodCreateCooldown;
    }

    public Rule getCommentCreateCooldown() {
        return commentCreateCooldown;
    }

    public void setCommentCreateCooldown(Rule commentCreateCooldown) {
        this.commentCreateCooldown = commentCreateCooldown;
    }

    public Rule getAuthPost() {
        return authPost;
    }

    public void setAuthPost(Rule authPost) {
        this.authPost = authPost;
    }

    public Rule getAuthPostCooldown() {
        return authPostCooldown;
    }

    public void setAuthPostCooldown(Rule authPostCooldown) {
        this.authPostCooldown = authPostCooldown;
    }

    public Rule getScanImage() {
        return scanImage;
    }

    public void setScanImage(Rule scanImage) {
        this.scanImage = scanImage;
    }

    public Rule getScanImageCooldown() {
        return scanImageCooldown;
    }

    public void setScanImageCooldown(Rule scanImageCooldown) {
        this.scanImageCooldown = scanImageCooldown;
    }

    public Rule getScanImageDaily() {
        return scanImageDaily;
    }

    public void setScanImageDaily(Rule scanImageDaily) {
        this.scanImageDaily = scanImageDaily;
    }

    public String getForwardedHeader() {
        return forwardedHeader;
    }

    public void setForwardedHeader(String forwardedHeader) {
        this.forwardedHeader = forwardedHeader;
    }

    public boolean isRequireTrustedProxy() {
        return requireTrustedProxy;
    }

    public void setRequireTrustedProxy(boolean requireTrustedProxy) {
        this.requireTrustedProxy = requireTrustedProxy;
    }

    public String getTrustedProxyCidrs() {
        return trustedProxyCidrs;
    }

    public void setTrustedProxyCidrs(String trustedProxyCidrs) {
        this.trustedProxyCidrs = trustedProxyCidrs;
    }

    public int getScanImageMaxConcurrency() {
        return scanImageMaxConcurrency;
    }

    public void setScanImageMaxConcurrency(int scanImageMaxConcurrency) {
        this.scanImageMaxConcurrency = scanImageMaxConcurrency;
    }

    public int getScanImageGlobalMaxConcurrency() {
        return scanImageGlobalMaxConcurrency;
    }

    public void setScanImageGlobalMaxConcurrency(int scanImageGlobalMaxConcurrency) {
        this.scanImageGlobalMaxConcurrency = scanImageGlobalMaxConcurrency;
    }

    public static class Rule {
        private int capacity = 30;
        private Duration window = Duration.ofHours(1);

        public Rule() {
        }

        public Rule(int capacity, Duration window) {
            this.capacity = capacity;
            this.window = window;
        }

        public int getCapacity() {
            return capacity;
        }

        public void setCapacity(int capacity) {
            this.capacity = capacity;
        }

        public Duration getWindow() {
            return window;
        }

        public void setWindow(Duration window) {
            this.window = window;
        }
    }
}

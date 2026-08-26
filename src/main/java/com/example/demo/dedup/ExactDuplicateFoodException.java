package com.example.demo.dedup;

public class ExactDuplicateFoodException extends RuntimeException {
    private final String canonicalProductKey;
    private final Long existingFoodId;

    public ExactDuplicateFoodException(String canonicalProductKey, Long existingFoodId) {
        super("Exact duplicate food detected");
        this.canonicalProductKey = canonicalProductKey;
        this.existingFoodId = existingFoodId;
    }

    public String getCanonicalProductKey() {
        return canonicalProductKey;
    }

    public Long getExistingFoodId() {
        return existingFoodId;
    }
}

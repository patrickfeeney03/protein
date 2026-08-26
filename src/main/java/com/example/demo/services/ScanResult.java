package com.example.demo.services;

import java.util.List;
import java.util.Map;

public record ScanResult(
        boolean scanSucceeded,
        List<String> warnings,
        List<String> disagreements,
        List<String> productDisagreements,
        ScanSource sourceUsed,
        boolean usedAnnotationFallback,
        ScanSource productSourceUsed,
        boolean productUsedAnnotationFallback,
        ParsedNutrition parsed,
        ProductDetails product,
        Map<String, RawNutrient> rawNutrients
) {
    public static ScanResult failed(List<String> warnings) {
        return new ScanResult(
                false,
                List.copyOf(warnings),
                List.of(),
                List.of(),
                ScanSource.NONE,
                false,
                ScanSource.NONE,
                false,
                ParsedNutrition.empty(),
                ProductDetails.empty(),
                Map.of()
        );
    }
}

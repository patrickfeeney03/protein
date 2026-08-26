package com.example.demo.services;

import java.util.Locale;
import java.util.Objects;

final class ScanUtils {
    private ScanUtils() {}

    static boolean hasParsedNutrition(ParsedNutrition parsed) {
        return parsed != null && (
                Objects.nonNull(parsed.servingSize())
                        || Objects.nonNull(parsed.caloriesPerServing())
                        || Objects.nonNull(parsed.caloriesPer100())
                        || Objects.nonNull(parsed.proteinPerServing())
                        || Objects.nonNull(parsed.proteinPer100())
                        || Objects.nonNull(parsed.carbsPerServing())
                        || Objects.nonNull(parsed.carbsPer100())
                        || Objects.nonNull(parsed.fatPerServing())
                        || Objects.nonNull(parsed.fatPer100())
        );
    }

    static boolean hasProductValues(ProductDetails product) {
        return product != null && (
                product.name() != null
                        || product.brand() != null
                        || product.barcodeNumber() != null
                        || product.storeName() != null
                        || product.servingsPerContainer() != null
                        || product.totalWeight() != null
                        || product.totalWeightUnit() != null
                        || product.drainedWeight() != null
                        || product.drainedWeightUnit() != null
        );
    }

    static String normalizeText(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9 ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    static String normalizeTextLenient(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}0-9 ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    static <T> T firstNonNull(T current, T candidate) {
        return current != null ? current : candidate;
    }

    static boolean hasMissingNutritionFields(ParsedNutrition parsed) {
        return parsed == null
                || parsed.servingSize() == null
                || parsed.servingUnit() == null
                || parsed.caloriesPerServing() == null
                || parsed.caloriesPer100() == null
                || parsed.proteinPerServing() == null
                || parsed.proteinPer100() == null
                || parsed.carbsPerServing() == null
                || parsed.carbsPer100() == null
                || parsed.fatPerServing() == null
                || parsed.fatPer100() == null;
    }

    static String cleanedText(String value) {
        if (value == null) {
            return null;
        }
        var cleaned = value.replaceAll("\\s+", " ").trim();
        return cleaned.isBlank() ? null : cleaned;
    }

    static String normalizeStoreName(String value) {
        var normalized = normalizeText(value);
        return switch (normalized) {
            case "aldi" -> "Aldi";
            case "lidl" -> "Lidl";
            case "tesco" -> "Tesco";
            case "dunnes", "dunnes stores" -> "Dunnes";
            default -> cleanedText(value);
        };
    }
}

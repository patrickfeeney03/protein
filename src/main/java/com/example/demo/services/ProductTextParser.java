package com.example.demo.services;

import com.example.demo.entities.FoodEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

class ProductTextParser {
    private static final Pattern PACKAGE_WEIGHT_PATTERN = Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*(kg|g|l|ml)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern DRAINED_WEIGHT_PATTERN = Pattern.compile("(?i)drained\\s*(?:weight|wt)?\\D{0,8}(\\d+(?:[.,]\\d+)?)\\s*(kg|g|l|ml)\\b");
    private static final Pattern SERVINGS_PER_CONTAINER_EXPLICIT_PATTERN = Pattern.compile(
            "(?i)servings?\\s+per\\s+container\\D{0,12}(\\d+(?:[.,]\\d+)?)"
    );
    private static final Pattern CONTAINS_SERVINGS_PATTERN = Pattern.compile(
            "(?i)(?:contains?|this\\s+(?:pack|can|bottle|bag|box|tray)\\s+contains?)\\D{0,12}(\\d+(?:[.,]\\d+)?)\\s+servings?\\b"
    );
    private static final List<String> STORE_NAMES = List.of("Aldi", "Lidl", "Tesco", "Dunnes");
    private static final List<String> PRODUCT_STOP_WORDS = List.of(
            "ingredients", "allergy advice", "nutrition", "typical values", "energy", "fat",
            "carbohydrate", "protein", "salt", "fibre", "sugars", "warning", "caution",
            "storage", "best before", "drained weight", "not to be sold separately",
            "this pack contains", "contains approx", "approx.", "approx ", " servings",
            " serving", " slices", " slice", "store in", "once opened", "consume within"
    );

    ProductDetails parseProductFromMarkdown(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return ProductDetails.empty();
        }

        var cleanedLines = cleanedLines(markdown);
        var storeName = inferStoreName(markdown);
        var name = extractProductName(cleanedLines);
        var brand = extractExplicitBrand(cleanedLines);
        if (brand == null) {
            brand = extractProductBrand(cleanedLines, name);
        }
        var servingsPerContainer = parseServingsPerContainer(markdown);
        var totalWeight = parsePackageWeight(cleanedLines);
        var totalWeightUnit = parsePackageWeightUnit(cleanedLines);
        var drainedWeight = parseDrainedWeight(cleanedLines);
        var drainedWeightUnit = parseDrainedWeightUnit(cleanedLines);

        return new ProductDetails(
                name,
                brand,
                null,
                storeName,
                servingsPerContainer,
                totalWeight,
                totalWeightUnit,
                drainedWeight,
                drainedWeightUnit
        );
    }

    private List<String> cleanedLines(String markdown) {
        var lines = markdown.split("\\R");
        var cleaned = new ArrayList<String>();
        for (String line : lines) {
            var normalized = line
                    .replaceAll("^#+\\s*", "")
                    .replaceAll("^\\s*>\\s*", "")
                    .replaceAll("^\\s*(?:[-*•▪▸◦⁃‣]|\\d+[.)])\\s*", "")
                    .replaceAll("!\\[[^]]*]\\([^)]*\\)", "")
                    .replaceAll("\\[[^]]*]\\([^)]*\\)", "")
                    .replace('*', ' ')
                    .replace('_', ' ')
                    .replaceAll("\\s+", " ")
                    .trim();
            if (!normalized.isBlank()) {
                cleaned.add(normalized);
            }
        }
        return cleaned;
    }

    private boolean containsStopWord(String normalizedLine) {
        return PRODUCT_STOP_WORDS.stream().anyMatch(normalizedLine::contains);
    }

    private boolean looksLikeProductName(String line) {
        var normalized = ScanUtils.normalizeText(line);
        if (normalized.isBlank()) {
            return false;
        }
        if (normalized.matches(".*\\b(?:contains?|approx|servings?|slices?|storage|opened|consume|days?)\\b.*")) {
            return false;
        }
        if (normalized.matches(".*\\d+\\s*(?:g|kg|ml|l)\\b.*")) {
            return false;
        }
        if (normalized.length() < 3) {
            return false;
        }
        return normalized.matches(".*[a-z].*");
    }

    private String inferStoreName(String markdown) {
        var lower = markdown.toLowerCase(Locale.ROOT);
        for (String storeName : STORE_NAMES) {
            if (lower.contains(storeName.toLowerCase(Locale.ROOT))) {
                return storeName;
            }
        }
        return null;
    }

    private Float parseServingsPerContainer(String markdown) {
        var normalized = markdown.replace('\n', ' ');
        var explicitMatcher = SERVINGS_PER_CONTAINER_EXPLICIT_PATTERN.matcher(normalized);
        if (explicitMatcher.find()) {
            return toFloat(explicitMatcher.group(1));
        }
        var containsServingsMatcher = CONTAINS_SERVINGS_PATTERN.matcher(normalized);
        if (containsServingsMatcher.find()) {
            return toFloat(containsServingsMatcher.group(1));
        }
        return null;
    }

    private Float parsePackageWeight(List<String> lines) {
        var match = findBestPackageWeightMatch(lines);
        return match == null ? null : match.normalizedValue();
    }

    private FoodEntity.Unit parsePackageWeightUnit(List<String> lines) {
        var match = findBestPackageWeightMatch(lines);
        return match == null ? null : match.unit();
    }

    private WeightMatch findBestPackageWeightMatch(List<String> lines) {
        WeightMatch bestMatch = null;
        for (String line : lines) {
            var normalizedLine = ScanUtils.normalizeText(line);
            if (containsStopWord(normalizedLine)) {
                continue;
            }
            var matcher = PACKAGE_WEIGHT_PATTERN.matcher(line);
            while (matcher.find()) {
                var normalizedUnit = normalizeWeightUnit(matcher.group(2));
                var normalizedValue = normalizeWeight(toFloat(matcher.group(1)), matcher.group(2));
                if (normalizedUnit == null || normalizedValue == null || normalizedValue <= 0f) {
                    continue;
                }
                var lower = line.toLowerCase(Locale.ROOT);
                if (lower.contains("per 100") || lower.contains("per tablespoon") || lower.contains("drained weight")
                        || lower.contains("serving") || lower.contains("%") || lower.contains("=")) {
                    continue;
                }
                if (bestMatch == null || normalizedValue > bestMatch.normalizedValue()) {
                    bestMatch = new WeightMatch(normalizedValue, normalizedUnit);
                }
            }
        }
        return bestMatch;
    }

    private Float parseDrainedWeight(List<String> lines) {
        var match = parseDrainedWeightMatch(lines);
        return match == null ? null : match.normalizedValue();
    }

    private FoodEntity.Unit parseDrainedWeightUnit(List<String> lines) {
        var match = parseDrainedWeightMatch(lines);
        return match == null ? null : match.unit();
    }

    private WeightMatch parseDrainedWeightMatch(List<String> lines) {
        for (String line : lines) {
            var matcher = DRAINED_WEIGHT_PATTERN.matcher(line);
            if (!matcher.find()) {
                continue;
            }
            var normalizedUnit = normalizeWeightUnit(matcher.group(2));
            var normalizedValue = normalizeWeight(toFloat(matcher.group(1)), matcher.group(2));
            if (normalizedUnit != null && normalizedValue != null && normalizedValue > 0f) {
                return new WeightMatch(normalizedValue, normalizedUnit);
            }
        }
        return null;
    }

    private String extractExplicitBrand(List<String> lines) {
        for (String line : lines) {
            var matcher = Pattern.compile("(?i)^brand\\s*[:\\-]\\s*(.+)$").matcher(line);
            if (matcher.find()) {
                return matcher.group(1).trim();
            }
        }
        return null;
    }

    private String extractProductName(List<String> lines) {
        for (String line : lines) {
            if (!looksLikeProductName(line)) {
                continue;
            }
            if (containsStopWord(ScanUtils.normalizeText(line))) {
                continue;
            }
            if (STORE_NAMES.stream().anyMatch(store -> store.equalsIgnoreCase(line.trim()))) {
                continue;
            }
            return line;
        }
        return null;
    }

    private String extractProductBrand(List<String> lines, String productName) {
        boolean passedName = productName == null;
        for (String line : lines) {
            if (!passedName) {
                if (ScanUtils.normalizeText(line).equals(ScanUtils.normalizeText(productName))) {
                    passedName = true;
                }
                continue;
            }

            if (!looksLikeProductName(line)) {
                continue;
            }
            if (containsStopWord(ScanUtils.normalizeText(line))) {
                continue;
            }
            if (productName != null && ScanUtils.normalizeText(line).equals(ScanUtils.normalizeText(productName))) {
                continue;
            }
            return line;
        }
        return null;
    }

    private Float normalizeWeight(Float rawValue, String rawUnit) {
        if (rawValue == null || rawUnit == null || rawUnit.isBlank()) {
            return null;
        }
        return switch (rawUnit.trim().toLowerCase(Locale.ROOT)) {
            case "kg", "l" -> rawValue * 1000f;
            case "g", "ml" -> rawValue;
            default -> null;
        };
    }

    private FoodEntity.Unit normalizeWeightUnit(String rawUnit) {
        if (rawUnit == null || rawUnit.isBlank()) {
            return null;
        }
        return switch (rawUnit.trim().toLowerCase(Locale.ROOT)) {
            case "kg", "g" -> FoodEntity.Unit.G;
            case "l", "ml" -> FoodEntity.Unit.ML;
            default -> null;
        };
    }

    private Float toFloat(String value) {
        try {
            return Float.parseFloat(value.replace(',', '.'));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private record WeightMatch(Float normalizedValue, FoodEntity.Unit unit) {
    }
}

package com.example.demo.services;

import com.example.demo.entities.FoodEntity;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class NutritionMarkdownParser {
    private static final Pattern NUMBER_PATTERN = Pattern.compile("(\\d+(?:[.,]\\d+)?)");
    private static final Pattern ENERGY_PATTERN = Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*kJ\\s*/\\s*(\\d+(?:[.,]\\d+)?)\\s*kcal", Pattern.CASE_INSENSITIVE);
    private static final Pattern KCAL_PATTERN = Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*kcal", Pattern.CASE_INSENSITIVE);
    static final Pattern SERVING_PATTERN = Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*(g|ml)", Pattern.CASE_INSENSITIVE);
    private static final Pattern SERVING_DECLARATION_PAREN_PATTERN = Pattern.compile("(?i)per\\s+(?!100\\s*(?:g|ml))[^\\n]{0,60}?\\((\\d+(?:[.,]\\d+)?)\\s*(g|ml)\\b");
    private static final Pattern SERVING_DECLARATION_INLINE_PATTERN = Pattern.compile("(?i)per\\s+(?!100\\s*(?:g|ml))(?:serving|portion|table?spoon|tea?spoon|slice|can|pack|bar|bottle|pot|cup|piece|burger|sausage|fillet|muffin|cookie|biscuit|wrap|bag|tray|half\\s+can|1/\\d+\\s+can)?[^\\n]{0,30}?(\\d+(?:[.,]\\d+)?)\\s*(g|ml)\\b");
    private static final Pattern PER_100_REFERENCE_PATTERN = Pattern.compile("(?i)per\\s*100\\s*(g|ml)\\b");
    private static final Pattern SERVING_CONTEXT_CUE_PATTERN = Pattern.compile("(?i)\\b(?:per|serving|portion|pack|bar|bottle|can|slice|cup|piece|contains|each)\\b");

    private static final Map<String, List<String>> METRIC_ALIASES = Map.of(
            "carbohydrate", List.of("carbohydrate", "carbohydrates", "carbs")
    );

    private final NutritionTableParser tableParser;

    NutritionMarkdownParser(NutritionTableParser tableParser) {
        this.tableParser = tableParser;
    }

    ParsedNutrition parseNutritionFromMarkdown(String markdown, Map<String, RawNutrient> rawNutrients) {
        if (markdown == null || markdown.isBlank()) {
            return ParsedNutrition.empty();
        }

        var markdownTableParsed = tableParser.parseMarkdownTable(markdown, rawNutrients);
        if (ScanUtils.hasParsedNutrition(markdownTableParsed)) {
            return markdownTableParsed;
        }

        Float servingSize = null;
        FoodEntity.Unit servingUnit = null;
        var servingMatch = parseServingSizeFromMarkdown(markdown);
        if (servingMatch != null) {
            servingSize = servingMatch.normalizedValue();
            servingUnit = servingMatch.unit();
            putIfText(rawNutrients, "SERVING_SIZE", servingMatch.rawText());
        }

        var lower = markdown.toLowerCase(Locale.ROOT);
        boolean hasStandalonePerServing = false;
        for (var line : lower.split("\n")) {
            if (line.contains("per serving") && !line.contains("per 100")) {
                hasStandalonePerServing = true;
                break;
            }
        }

        return new ParsedNutrition(
                servingSize,
                servingUnit,
                hasStandalonePerServing ? parseMetricFromMarkdown(markdown, "per serving", "energy", true, rawNutrients, "ENERGY_KCAL_SERVING") : null,
                parseMetricFromMarkdown(markdown, "per 100", "energy", true, rawNutrients, "ENERGY_KCAL_100G"),
                hasStandalonePerServing ? parseMetricFromMarkdown(markdown, "per serving", "protein", false, rawNutrients, "PROTEINS_SERVING") : null,
                parseMetricFromMarkdown(markdown, "per 100", "protein", false, rawNutrients, "PROTEINS_100G"),
                hasStandalonePerServing ? parseMetricFromMarkdown(markdown, "per serving", "carbohydrate", false, rawNutrients, "CARBOHYDRATES_SERVING") : null,
                parseMetricFromMarkdown(markdown, "per 100", "carbohydrate", false, rawNutrients, "CARBOHYDRATES_100G"),
                hasStandalonePerServing ? parseMetricFromMarkdown(markdown, "per serving", "fat", false, rawNutrients, "FAT_SERVING") : null,
                parseMetricFromMarkdown(markdown, "per 100", "fat", false, rawNutrients, "FAT_100G")
        );
    }

    Float parseMetricFromMarkdown(
            String markdown,
            String sectionLabel,
            String metricLabel,
            boolean energy,
            Map<String, RawNutrient> rawNutrients,
            String rawKey
    ) {
        var lower = markdown.toLowerCase(Locale.ROOT);
        var sectionIndex = lower.indexOf(sectionLabel);
        if (sectionIndex < 0) {
            return null;
        }

        Integer bestMetricIndex = null;
        var aliases = METRIC_ALIASES.getOrDefault(metricLabel, List.of(metricLabel));
        for (var alias : aliases) {
            var idx = lower.indexOf(alias, sectionIndex);
            if (idx >= 0 && (bestMetricIndex == null || idx < bestMetricIndex)) {
                bestMetricIndex = idx;
            }
        }
        if (bestMetricIndex == null) {
            return null;
        }

        var snippetEnd = Math.min(markdown.length(), bestMetricIndex + 64);
        var snippet = markdown.substring(bestMetricIndex, snippetEnd);
        putIfText(rawNutrients, rawKey, snippet);
        return energy ? parseEnergyKcal(snippet) : parseNumber(snippet);
    }

    ServingMatch parseServingSizeFromMarkdown(String markdown) {
        var parenthesizedMatch = findServingDeclaration(markdown, SERVING_DECLARATION_PAREN_PATTERN);
        if (parenthesizedMatch != null) {
            return parenthesizedMatch;
        }

        var inlineMatch = findServingDeclaration(markdown, SERVING_DECLARATION_INLINE_PATTERN);
        if (inlineMatch != null) {
            return inlineMatch;
        }

        var genericMatch = SERVING_PATTERN.matcher(markdown);
        while (genericMatch.find()) {
            var rawText = genericMatch.group();
            if (rawText == null || rawText.isBlank()) {
                continue;
            }
            var start = genericMatch.start();
            var end = genericMatch.end();
            var windowStart = Math.max(0, start - 12);
            var contextStart = Math.max(0, start - 36);
            var windowEnd = Math.min(markdown.length(), end + 6);
            var prefix = markdown.substring(windowStart, start).toLowerCase(Locale.ROOT);
            var broaderContext = markdown.substring(contextStart, windowEnd).toLowerCase(Locale.ROOT);
            var context = markdown.substring(windowStart, windowEnd);
            if (prefix.contains("per 100") || PER_100_REFERENCE_PATTERN.matcher(context).find()) {
                continue;
            }
            if (broaderContext.contains("energy") || broaderContext.contains("fat")
                    || broaderContext.contains("carbohydrate") || broaderContext.contains("protein")
                    || broaderContext.contains("sugar") || broaderContext.contains("salt")
                    || broaderContext.contains("fibre")) {
                continue;
            }
            if (!SERVING_CONTEXT_CUE_PATTERN.matcher(broaderContext).find()) {
                continue;
            }
            return new ServingMatch(
                    toFloat(genericMatch.group(1)),
                    "ml".equalsIgnoreCase(genericMatch.group(2)) ? FoodEntity.Unit.ML : FoodEntity.Unit.G,
                    rawText
            );
        }

        return null;
    }

    private ServingMatch findServingDeclaration(String markdown, Pattern pattern) {
        var matcher = pattern.matcher(markdown);
        if (!matcher.find()) {
            return null;
        }
        return new ServingMatch(
                toFloat(matcher.group(1)),
                "ml".equalsIgnoreCase(matcher.group(2)) ? FoodEntity.Unit.ML : FoodEntity.Unit.G,
                matcher.group()
        );
    }

    static Float parseEnergyKcal(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        Matcher energyMatcher = ENERGY_PATTERN.matcher(raw);
        if (energyMatcher.find()) {
            return toFloat(energyMatcher.group(2));
        }

        Matcher kcalMatcher = KCAL_PATTERN.matcher(raw);
        if (kcalMatcher.find()) {
            return toFloat(kcalMatcher.group(1));
        }

        return null;
    }

    static Float parseNumber(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        var matcher = NUMBER_PATTERN.matcher(raw);
        if (!matcher.find()) {
            return null;
        }

        return toFloat(matcher.group(1));
    }

    static Float toFloat(String value) {
        try {
            return Float.parseFloat(value.replace(',', '.'));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static void putIfText(Map<String, RawNutrient> rawNutrients, String key, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        rawNutrients.putIfAbsent(key, new RawNutrient(text));
    }

    record ServingMatch(Float normalizedValue, FoodEntity.Unit unit, String rawText) {
    }
}

package com.example.demo.services;

import com.example.demo.entities.FoodEntity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

class AnnotationParser {
    private static final Logger logger = LoggerFactory.getLogger(AnnotationParser.class);
    private static final Pattern NUMBER_PATTERN = Pattern.compile("(\\d+(?:[.,]\\d+)?)");

    private final ObjectMapper objectMapper;

    AnnotationParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    JsonNode parse(JsonNode node, List<String> warnings) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }

        if (node.isObject()) {
            return node;
        }

        if (!node.isTextual()) {
            warnings.add("Unexpected document annotation shape from Mistral OCR");
            return null;
        }

        var annotationText = node.asText();
        if (annotationText == null || annotationText.isBlank()) {
            return null;
        }

        try {
            return objectMapper.readTree(annotationText);
        } catch (JsonProcessingException e) {
            warnings.add("Failed to parse document annotation");
            logger.error("Failed to parse document annotation: {}", annotationText, e);
            return null;
        }
    }

    ParsedNutrition parseNutrition(JsonNode annotationNode, Map<String, RawNutrient> rawNutrients) {
        if (annotationNode == null || annotationNode.isMissingNode() || annotationNode.isNull()) {
            return ParsedNutrition.empty();
        }

        var servingSize = floatValue(annotationNode.path("serving_size"));
        var servingUnit = unitValue(annotationNode.path("serving_unit").asText(null));
        var perServing = annotationNode.path("macros_per_serving");
        var per100 = annotationNode.path("macros_per_100");

        addRaw(rawNutrients, "SERVING_SIZE", servingSize, servingUnit == null ? null : servingUnit.name().toLowerCase(Locale.ROOT));
        addRaw(rawNutrients, "ENERGY_KCAL_SERVING", floatValue(perServing.path("energy_kcal")), "kcal");
        addRaw(rawNutrients, "FAT_SERVING", floatValue(perServing.path("fat_g")), "g");
        addRaw(rawNutrients, "CARBOHYDRATES_SERVING", floatValue(perServing.path("carbohydrate_g")), "g");
        addRaw(rawNutrients, "PROTEINS_SERVING", floatValue(perServing.path("protein_g")), "g");
        addRaw(rawNutrients, "ENERGY_KCAL_100G", floatValue(per100.path("energy_kcal")), "kcal");
        addRaw(rawNutrients, "FAT_100G", floatValue(per100.path("fat_g")), "g");
        addRaw(rawNutrients, "CARBOHYDRATES_100G", floatValue(per100.path("carbohydrate_g")), "g");
        addRaw(rawNutrients, "PROTEINS_100G", floatValue(per100.path("protein_g")), "g");

        return new ParsedNutrition(
                servingSize,
                servingUnit,
                floatValue(perServing.path("energy_kcal")),
                floatValue(per100.path("energy_kcal")),
                floatValue(perServing.path("protein_g")),
                floatValue(per100.path("protein_g")),
                floatValue(perServing.path("carbohydrate_g")),
                floatValue(per100.path("carbohydrate_g")),
                floatValue(perServing.path("fat_g")),
                floatValue(per100.path("fat_g"))
        );
    }

    ProductDetails parseProduct(JsonNode annotationNode) {
        if (annotationNode == null || annotationNode.isMissingNode() || annotationNode.isNull()) {
            return ProductDetails.empty();
        }

        return new ProductDetails(
                ScanUtils.cleanedText(annotationNode.path("name").asText(null)),
                ScanUtils.cleanedText(annotationNode.path("brand").asText(null)),
                ScanUtils.cleanedText(annotationNode.path("barcode_number").asText(null)),
                ScanUtils.normalizeStoreName(annotationNode.path("store_name").asText(null)),
                floatValue(annotationNode.path("servings_per_container")),
                normalizeWeight(annotationNode.path("total_weight"), annotationNode.path("total_weight_unit").asText(null)),
                normalizeWeightUnit(annotationNode.path("total_weight_unit").asText(null)),
                normalizeWeight(annotationNode.path("drained_weight"), annotationNode.path("drained_weight_unit").asText(null)),
                normalizeWeightUnit(annotationNode.path("drained_weight_unit").asText(null))
        );
    }

    private Float floatValue(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return (float) node.asDouble();
        }
        if (node.isTextual()) {
            return parseNumber(node.asText());
        }
        return null;
    }

    private FoodEntity.Unit unitValue(String rawUnit) {
        if (rawUnit == null || rawUnit.isBlank()) {
            return null;
        }
        return "ml".equalsIgnoreCase(rawUnit.trim()) ? FoodEntity.Unit.ML : FoodEntity.Unit.G;
    }

    private Float normalizeWeight(JsonNode valueNode, String rawUnit) {
        var rawValue = floatValue(valueNode);
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

    private void addRaw(Map<String, RawNutrient> rawNutrients, String key, Float value, String unit) {
        if (value == null) {
            return;
        }
        putIfText(rawNutrients, key, value + (unit == null || unit.isBlank() ? "" : " " + unit));
    }

    private void putIfText(Map<String, RawNutrient> rawNutrients, String key, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        rawNutrients.putIfAbsent(key, new RawNutrient(text));
    }

    private Float parseNumber(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        var matcher = NUMBER_PATTERN.matcher(raw);
        if (!matcher.find()) {
            return null;
        }
        return toFloat(matcher.group(1));
    }

    private Float toFloat(String value) {
        try {
            return Float.parseFloat(value.replace(',', '.'));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}

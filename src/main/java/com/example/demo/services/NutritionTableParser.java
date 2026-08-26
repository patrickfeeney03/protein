package com.example.demo.services;

import com.example.demo.entities.FoodEntity;
import com.fasterxml.jackson.databind.JsonNode;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

class NutritionTableParser {

    ParsedNutrition parseNutritionFromTables(JsonNode tablesNode, Map<String, RawNutrient> rawNutrients) {
        for (JsonNode tableNode : tablesNode) {
            var html = tableNode.path("content").asText("");
            if (html.isBlank()) {
                continue;
            }

            var parsed = parseHtmlTable(html, rawNutrients);
            if (ScanUtils.hasParsedNutrition(parsed)) {
                return parsed;
            }
        }

        return ParsedNutrition.empty();
    }

    ParsedNutrition parseMarkdownTable(String markdown, Map<String, RawNutrient> rawNutrients) {
        var rows = new ArrayList<List<String>>();
        for (String line : markdown.split("\\R")) {
            if (!line.contains("|")) {
                continue;
            }
            var trimmed = line.trim();
            if (trimmed.isBlank()) {
                continue;
            }
            var cells = new ArrayList<String>();
            for (String part : trimmed.split("\\|", -1)) {
                var cell = part.trim();
                if (!cell.isEmpty()) {
                    cells.add(cell);
                }
            }
            if (cells.isEmpty() || cells.stream().allMatch(cell -> cell.matches("-+"))) {
                continue;
            }
            rows.add(List.copyOf(cells));
        }
        if (rows.isEmpty()) {
            return ParsedNutrition.empty();
        }
        return parseNormalizedNutritionTable(rows, rawNutrients);
    }

    private ParsedNutrition parseHtmlTable(String html, Map<String, RawNutrient> rawNutrients) {
        var document = Jsoup.parse(html);
        var rows = document.select("tr");
        if (rows.isEmpty()) {
            return ParsedNutrition.empty();
        }

        var normalizedRows = new ArrayList<List<String>>();
        for (Element row : rows) {
            var cells = row.select("th,td");
            if (cells.isEmpty()) {
                continue;
            }
            var normalizedRow = new ArrayList<String>();
            for (Element cell : cells) {
                normalizedRow.add(cell.text());
            }
            normalizedRows.add(List.copyOf(normalizedRow));
        }

        return parseNormalizedNutritionTable(normalizedRows, rawNutrients);
    }

    private ParsedNutrition parseNormalizedNutritionTable(List<List<String>> rows, Map<String, RawNutrient> rawNutrients) {
        int headerRowIndex = findHeaderRowIndex(rows);
        if (headerRowIndex < 0) {
            return ParsedNutrition.empty();
        }

        var headerCells = rows.get(headerRowIndex);
        int per100Index = findPer100ColumnIndex(headerCells);
        int perServingIndex = findServingColumnIndex(headerCells, per100Index);
        if (per100Index < 0 && perServingIndex < 0) {
            return ParsedNutrition.empty();
        }

        Float caloriesPerServing = null;
        Float caloriesPer100 = null;
        Float proteinPerServing = null;
        Float proteinPer100 = null;
        Float carbsPerServing = null;
        Float carbsPer100 = null;
        Float fatPerServing = null;
        Float fatPer100 = null;
        Float servingSize = perServingIndex >= 0 ? parseServingSizeFromHeader(headerCells.get(perServingIndex)) : null;
        FoodEntity.Unit servingUnit = perServingIndex >= 0 ? parseServingUnitFromHeader(headerCells.get(perServingIndex)) : null;

        for (int i = headerRowIndex + 1; i < rows.size(); i++) {
            var cells = rows.get(i);
            if (cells.isEmpty()) {
                continue;
            }

            var labelType = classifyRowLabel(cells.getFirst());
            if (labelType == RowLabelType.UNKNOWN) {
                continue;
            }

            var per100Text = valueAt(cells, per100Index);
            var perServingText = valueAt(cells, perServingIndex);
            switch (labelType) {
                case ENERGY -> {
                    caloriesPer100 = ScanUtils.firstNonNull(caloriesPer100, NutritionMarkdownParser.parseEnergyKcal(per100Text));
                    caloriesPerServing = ScanUtils.firstNonNull(caloriesPerServing, NutritionMarkdownParser.parseEnergyKcal(perServingText));
                    NutritionMarkdownParser.putIfText(rawNutrients, "ENERGY_KCAL_100G", per100Text);
                    NutritionMarkdownParser.putIfText(rawNutrients, "ENERGY_KCAL_SERVING", perServingText);
                }
                case FAT -> {
                    fatPer100 = ScanUtils.firstNonNull(fatPer100, NutritionMarkdownParser.parseNumber(per100Text));
                    fatPerServing = ScanUtils.firstNonNull(fatPerServing, NutritionMarkdownParser.parseNumber(perServingText));
                    NutritionMarkdownParser.putIfText(rawNutrients, "FAT_100G", per100Text);
                    NutritionMarkdownParser.putIfText(rawNutrients, "FAT_SERVING", perServingText);
                }
                case CARBOHYDRATE -> {
                    carbsPer100 = ScanUtils.firstNonNull(carbsPer100, NutritionMarkdownParser.parseNumber(per100Text));
                    carbsPerServing = ScanUtils.firstNonNull(carbsPerServing, NutritionMarkdownParser.parseNumber(perServingText));
                    NutritionMarkdownParser.putIfText(rawNutrients, "CARBOHYDRATES_100G", per100Text);
                    NutritionMarkdownParser.putIfText(rawNutrients, "CARBOHYDRATES_SERVING", perServingText);
                }
                case PROTEIN -> {
                    proteinPer100 = ScanUtils.firstNonNull(proteinPer100, NutritionMarkdownParser.parseNumber(per100Text));
                    proteinPerServing = ScanUtils.firstNonNull(proteinPerServing, NutritionMarkdownParser.parseNumber(perServingText));
                    NutritionMarkdownParser.putIfText(rawNutrients, "PROTEINS_100G", per100Text);
                    NutritionMarkdownParser.putIfText(rawNutrients, "PROTEINS_SERVING", perServingText);
                }
                default -> {
                }
            }
        }

        if (servingSize != null) {
            NutritionMarkdownParser.putIfText(rawNutrients, "SERVING_SIZE", servingSize + " " + (servingUnit == null ? "" : servingUnit.name().toLowerCase(Locale.ROOT)).trim());
        }

        var parsed = new ParsedNutrition(
                servingSize,
                servingUnit,
                caloriesPerServing,
                caloriesPer100,
                proteinPerServing,
                proteinPer100,
                carbsPerServing,
                carbsPer100,
                fatPerServing,
                fatPer100
        );
        return hasTableConfidence(parsed) ? parsed : ParsedNutrition.empty();
    }

    private int findHeaderRowIndex(List<List<String>> rows) {
        for (int i = 0; i < rows.size(); i++) {
            var headerCells = rows.get(i);
            if (headerCells.isEmpty()) {
                continue;
            }
            int per100Index = findPer100ColumnIndex(headerCells);
            int perServingIndex = findServingColumnIndex(headerCells, per100Index);
            if (per100Index >= 0 || perServingIndex >= 0) {
                return i;
            }
        }
        return -1;
    }

    private int findPer100ColumnIndex(List<String> headerCells) {
        for (int i = 0; i < headerCells.size(); i++) {
            if (classifyColumnRole(headerCells.get(i)) == ColumnRole.PER_100) {
                return i;
            }
        }
        return -1;
    }

    private int findServingColumnIndex(List<String> headerCells, int per100Index) {
        int bestIndex = -1;
        int bestScore = Integer.MIN_VALUE;
        for (int i = 0; i < headerCells.size(); i++) {
            if (i == per100Index) {
                continue;
            }
            var role = classifyColumnRole(headerCells.get(i));
            if (role == ColumnRole.PERCENT || role == ColumnRole.PER_100) {
                continue;
            }
            int score = scoreServingColumn(headerCells.get(i));
            if (score > bestScore) {
                bestScore = score;
                bestIndex = i;
            }
        }
        return bestScore > 0 ? bestIndex : -1;
    }

    private String valueAt(List<String> cells, int index) {
        if (index < 0 || index >= cells.size()) {
            return null;
        }
        var value = cells.get(index);
        return value == null || value.isBlank() ? null : value;
    }

    private String normalizeHeaderText(String headerText) {
        return headerText == null
                ? ""
                : headerText.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    private ColumnRole classifyColumnRole(String headerText) {
        var normalized = normalizeHeaderText(headerText);
        if (normalized.isBlank()) {
            return ColumnRole.UNKNOWN;
        }
        if (normalized.contains("%") || normalized.contains("nrv") || normalized.contains("ri")) {
            return ColumnRole.PERCENT;
        }
        if (normalized.contains("100g") || normalized.contains("100ml") || normalized.contains("per100")) {
            return ColumnRole.PER_100;
        }
        if (normalized.matches(".*\\d+(?:[.,]\\d+)?(?:g|ml)\\b.*")
                || normalized.contains("perserving")
                || normalized.contains("perbar")
                || normalized.contains("perportion")
                || normalized.contains("perpack")
                || normalized.contains("perslice")
                || normalized.contains("perbottle")
                || normalized.contains("percan")) {
            return ColumnRole.PER_SERVING;
        }
        return ColumnRole.UNKNOWN;
    }

    private int scoreServingColumn(String headerText) {
        var normalized = normalizeHeaderText(headerText);
        int score = 0;
        if (classifyColumnRole(headerText) == ColumnRole.PER_SERVING) {
            score += 10;
        }
        if (normalized.contains("per")) {
            score += 3;
        }
        if (normalized.contains("/") || normalized.contains("(")) {
            score += 2;
        }
        if (normalized.matches(".*\\d+(?:[.,]\\d+)?(?:g|ml)\\b.*")) {
            score += 4;
        }
        return score;
    }

    private RowLabelType classifyRowLabel(String labelText) {
        var normalized = ScanUtils.normalizeText(labelText);
        if (normalized.startsWith("energy")) {
            return RowLabelType.ENERGY;
        }
        if (normalized.equals("fat") || normalized.startsWith("total fat")) {
            return RowLabelType.FAT;
        }
        if (normalized.startsWith("carbohydrate") || normalized.startsWith("carbohydrates") || normalized.startsWith("carbs")) {
            return RowLabelType.CARBOHYDRATE;
        }
        if (normalized.startsWith("protein") || normalized.startsWith("proteins")) {
            return RowLabelType.PROTEIN;
        }
        return RowLabelType.UNKNOWN;
    }

    private boolean hasTableConfidence(ParsedNutrition parsed) {
        int populated = 0;
        if (parsed.caloriesPer100() != null || parsed.caloriesPerServing() != null) {
            populated++;
        }
        if (parsed.proteinPer100() != null || parsed.proteinPerServing() != null) {
            populated++;
        }
        if (parsed.carbsPer100() != null || parsed.carbsPerServing() != null) {
            populated++;
        }
        if (parsed.fatPer100() != null || parsed.fatPerServing() != null) {
            populated++;
        }
        return populated >= 2;
    }

    private Float parseServingSizeFromHeader(String headerText) {
        if (headerText == null) {
            return null;
        }
        var matcher = NutritionMarkdownParser.SERVING_PATTERN.matcher(headerText);
        return matcher.find() ? NutritionMarkdownParser.toFloat(matcher.group(1)) : null;
    }

    private FoodEntity.Unit parseServingUnitFromHeader(String headerText) {
        if (headerText == null) {
            return null;
        }
        var matcher = NutritionMarkdownParser.SERVING_PATTERN.matcher(headerText);
        if (!matcher.find()) {
            return null;
        }
        return "ml".equalsIgnoreCase(matcher.group(2)) ? FoodEntity.Unit.ML : FoodEntity.Unit.G;
    }

    private enum ColumnRole {
        PER_100,
        PER_SERVING,
        PERCENT,
        UNKNOWN
    }

    private enum RowLabelType {
        ENERGY,
        FAT,
        CARBOHYDRATE,
        PROTEIN,
        UNKNOWN
    }
}

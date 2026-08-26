package com.example.demo.services;

import com.example.demo.entities.FoodEntity;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.*;

class NutritionTableParserTest {

    private final NutritionTableParser parser = new NutritionTableParser();

    // -- parseMarkdownTable: edge cases --

    @Test
    void parseMarkdownTable_throwsNpeForNull() {
        assertThrows(NullPointerException.class,
                () -> parser.parseMarkdownTable(null, new LinkedHashMap<>()));
    }

    @Test
    void parseMarkdownTable_returnsEmptyForBlank() {
        var result = parser.parseMarkdownTable("   ", new LinkedHashMap<>());
        assertEquals(ParsedNutrition.empty(), result);
    }

    @Test
    void parseMarkdownTable_returnsEmptyForNoPipeChars() {
        var result = parser.parseMarkdownTable("No table here\nJust text", new LinkedHashMap<>());
        assertEquals(ParsedNutrition.empty(), result);
    }

    @Test
    void parseMarkdownTable_returnsEmptyForSeparatorOnlyRows() {
        var result = parser.parseMarkdownTable("---|---|---|---", new LinkedHashMap<>());
        assertEquals(ParsedNutrition.empty(), result);
    }

    @Test
    void parseMarkdownTable_returnsEmptyWhenNoRelevantHeader() {
        var markdown = """
                | Nutrition Facts | | |
                | --- | --- | --- |
                | Energy | 239 kcal | 96 kcal |
                """;
        var result = parser.parseMarkdownTable(markdown, new LinkedHashMap<>());
        assertEquals(ParsedNutrition.empty(), result);
    }

    @Test
    void parseMarkdownTable_returnsEmptyForPercentOnlyColumn() {
        var markdown = """
                | Typical Values | % RI |
                | --- | --- |
                | Energy | 12% |
                | Fat | 6% |
                """;
        var result = parser.parseMarkdownTable(markdown, new LinkedHashMap<>());
        assertEquals(ParsedNutrition.empty(), result);
    }

    // -- parseMarkdownTable: header detection variants --

    @Test
    void parseMarkdownTable_detectsPer100mlHeader() {
        var markdown = """
                | Typical Values | Per 100ml |
                | --- | --- |
                | Energy | 1000 kJ / 239 kcal |
                | Fat | 4.1g |
                | Carbohydrate | 61.7g |
                | Protein | 12.2g |
                """;
        var result = parser.parseMarkdownTable(markdown, new LinkedHashMap<>());
        assertEquals(239f, result.caloriesPer100());
        assertEquals(4.1f, result.fatPer100());
        assertNull(result.servingSize());
    }

    @Test
    void parseMarkdownTable_detectsPerServingColumnWithWeight() {
        var markdown = """
                | Typical Values | per 100g | per 40g |
                | --- | --- | --- |
                | Energy | 239 kcal | 96 kcal |
                | Fat | 4.1g | 1.6g |
                | Carbohydrate | 61.7g | 24.7g |
                | Protein | 12.2g | 4.9g |
                """;
        var raw = new LinkedHashMap<String, RawNutrient>();
        var result = parser.parseMarkdownTable(markdown, raw);
        assertEquals(239f, result.caloriesPer100());
        assertEquals(96f, result.caloriesPerServing());
        assertEquals(40f, result.servingSize());
        assertEquals(FoodEntity.Unit.G, result.servingUnit());
    }

    // -- parseMarkdownTable: extract per 100 only --

    @Test
    void parseMarkdownTable_extractsPer100Only() {
        var markdown = """
                | Typical Values | Per 100g |
                | --- | --- |
                | Energy | 1000 kJ / 239 kcal |
                | Fat | 4.1g |
                | Carbohydrate | 61.7g |
                | Protein | 12.2g |
                """;
        var result = parser.parseMarkdownTable(markdown, new LinkedHashMap<>());
        assertEquals(239f, result.caloriesPer100());
        assertEquals(4.1f, result.fatPer100());
        assertEquals(61.7f, result.carbsPer100());
        assertEquals(12.2f, result.proteinPer100());
        assertNull(result.caloriesPerServing());
        assertNull(result.servingSize());
    }

    // -- parseMarkdownTable: extract per serving only --

    @Test
    void parseMarkdownTable_extractsPerServingOnly() {
        var markdown = """
                | Typical Values | Per 40g serving |
                | --- | --- |
                | Energy | 96 kcal |
                | Fat | 1.6g |
                | Carbohydrate | 24.7g |
                | Protein | 4.9g |
                """;
        var result = parser.parseMarkdownTable(markdown, new LinkedHashMap<>());
        assertNull(result.caloriesPer100());
        assertEquals(96f, result.caloriesPerServing());
        assertEquals(1.6f, result.fatPerServing());
        assertEquals(24.7f, result.carbsPerServing());
        assertEquals(4.9f, result.proteinPerServing());
        assertEquals(40f, result.servingSize());
        assertEquals(FoodEntity.Unit.G, result.servingUnit());
    }

    // -- parseMarkdownTable: hasTableConfidence (needs >= 2 categories) --

    @Test
    void parseMarkdownTable_returnsEmptyForSingleNutrientCategory() {
        var markdown = """
                | Typical Values | Per 100g |
                | --- | --- |
                | Energy | 239 kcal |
                """;
        var result = parser.parseMarkdownTable(markdown, new LinkedHashMap<>());
        assertEquals(ParsedNutrition.empty(), result);
    }

    @Test
    void parseMarkdownTable_succeedsWithTwoNutrientCategories() {
        var markdown = """
                | Typical Values | Per 100g |
                | --- | --- |
                | Energy | 239 kcal |
                | Fat | 4.1g |
                """;
        var result = parser.parseMarkdownTable(markdown, new LinkedHashMap<>());
        assertEquals(239f, result.caloriesPer100());
        assertEquals(4.1f, result.fatPer100());
        assertNull(result.proteinPer100());
        assertNull(result.carbsPer100());
    }

    // -- parseMarkdownTable: row label variants --

    @Test
    void parseMarkdownTable_recognizesTotalFatLabel() {
        var markdown = """
                | Typical Values | Per 100g | Per 40g |
                | --- | --- | --- |
                | Energy | 239 kcal | 96 kcal |
                | Total Fat | 4.1g | 1.6g |
                | Carbohydrate | 61.7g | 24.7g |
                | Protein | 12.2g | 4.9g |
                """;
        var result = parser.parseMarkdownTable(markdown, new LinkedHashMap<>());
        assertEquals(4.1f, result.fatPer100());
        assertEquals(1.6f, result.fatPerServing());
    }

    @Test
    void parseMarkdownTable_recognizesCarbsLabel() {
        var markdown = """
                | Typical Values | Per 100g |
                | --- | --- |
                | Energy | 239 kcal |
                | Carbs | 61.7g |
                """;
        var result = parser.parseMarkdownTable(markdown, new LinkedHashMap<>());
        assertEquals(239f, result.caloriesPer100());
        assertEquals(61.7f, result.carbsPer100());
    }

    @Test
    void parseMarkdownTable_recognizesProteinsLabel() {
        var markdown = """
                | Typical Values | Per 100g |
                | --- | --- |
                | Energy | 239 kcal |
                | Proteins | 12.2g |
                """;
        var result = parser.parseMarkdownTable(markdown, new LinkedHashMap<>());
        assertEquals(12.2f, result.proteinPer100());
    }

    @Test
    void parseMarkdownTable_skipsUnknownRowLabels() {
        var markdown = """
                | Typical Values | Per 100g |
                | --- | --- |
                | Energy | 239 kcal |
                | Fat | 4.1g |
                | Fibre | 3.0g |
                | Salt | 0.5g |
                | Carbohydrate | 61.7g |
                | Protein | 12.2g |
                """;
        var result = parser.parseMarkdownTable(markdown, new LinkedHashMap<>());
        assertEquals(239f, result.caloriesPer100());
        assertEquals(4.1f, result.fatPer100());
        assertEquals(61.7f, result.carbsPer100());
        assertEquals(12.2f, result.proteinPer100());
    }

    // -- parseMarkdownTable: raw nutrients populated --

    @Test
    void parseMarkdownTable_populatesRawNutrients() {
        var markdown = """
                | Typical Values | Per 100g | Per 40g |
                | --- | --- | --- |
                | Energy | 1000 kJ / 239 kcal | 400 kJ / 96 kcal |
                | Fat | 4.1g | 1.6g |
                | Carbohydrate | 61.7g | 24.7g |
                | Protein | 12.2g | 4.9g |
                """;
        var raw = new LinkedHashMap<String, RawNutrient>();
        parser.parseMarkdownTable(markdown, raw);
        assertTrue(raw.containsKey("ENERGY_KCAL_100G"));
        assertTrue(raw.containsKey("ENERGY_KCAL_SERVING"));
        assertTrue(raw.containsKey("FAT_100G"));
        assertTrue(raw.containsKey("FAT_SERVING"));
        assertTrue(raw.containsKey("CARBOHYDRATES_100G"));
        assertTrue(raw.containsKey("CARBOHYDRATES_SERVING"));
        assertTrue(raw.containsKey("PROTEINS_100G"));
        assertTrue(raw.containsKey("PROTEINS_SERVING"));
        assertTrue(raw.containsKey("SERVING_SIZE"));
    }

    @Test
    void parseMarkdownTable_rawNutrientsUsePutIfAbsent() {
        var markdown = """
                | Typical Values | Per 100g |
                | --- | --- |
                | Energy | 239 kcal |
                | Fat | 4.1g |
                """;
        var raw = new LinkedHashMap<String, RawNutrient>();
        raw.put("ENERGY_KCAL_100G", new RawNutrient("existing"));
        parser.parseMarkdownTable(markdown, raw);
        assertEquals("existing", raw.get("ENERGY_KCAL_100G").text());
    }

    // -- parseMarkdownTable: serving size and unit variants --

    @Test
    void parseMarkdownTable_parsesServingSizeInMl() {
        var markdown = """
                | Typical Values | Per 100ml | Per 200ml |
                | --- | --- | --- |
                | Energy | 100 kcal | 200 kcal |
                | Fat | 2.0g | 4.0g |
                | Carbohydrate | 5.0g | 10.0g |
                | Protein | 1.0g | 2.0g |
                """;
        var result = parser.parseMarkdownTable(markdown, new LinkedHashMap<>());
        assertEquals(200f, result.servingSize());
        assertEquals(FoodEntity.Unit.ML, result.servingUnit());
    }

    @Test
    void parseMarkdownTable_parsesServingSizeFromPortionHeader() {
        var markdown = """
                | Typical Values | per 100g | per portion (30g) |
                | --- | --- | --- |
                | Energy | 239 kcal | 72 kcal |
                | Fat | 4.1g | 1.2g |
                | Carbs | 61.7g | 18.5g |
                | Protein | 12.2g | 3.7g |
                """;
        var result = parser.parseMarkdownTable(markdown, new LinkedHashMap<>());
        assertEquals(30f, result.servingSize());
        assertEquals(FoodEntity.Unit.G, result.servingUnit());
    }

    // -- parseMarkdownTable: column ordering --

    @Test
    void parseMarkdownTable_handlesReversedColumnOrder() {
        var markdown = """
                | Typical Values | Per 40g | Per 100g |
                | --- | --- | --- |
                | Energy | 96 kcal | 239 kcal |
                | Fat | 1.6g | 4.1g |
                | Carbohydrate | 24.7g | 61.7g |
                | Protein | 4.9g | 12.2g |
                """;
        var result = parser.parseMarkdownTable(markdown, new LinkedHashMap<>());
        assertEquals(239f, result.caloriesPer100());
        assertEquals(96f, result.caloriesPerServing());
        assertEquals(40f, result.servingSize());
    }

    @Test
    void parseMarkdownTable_skipsPercentColumnBetween() {
        var markdown = """
                | Typical Values | Per 100g | % RI | Per 40g |
                | --- | --- | --- | --- |
                | Energy | 239 kcal | 12% | 96 kcal |
                | Fat | 4.1g | 6% | 1.6g |
                | Carbohydrate | 61.7g | 24% | 24.7g |
                | Protein | 12.2g | 24% | 4.9g |
                """;
        var result = parser.parseMarkdownTable(markdown, new LinkedHashMap<>());
        assertEquals(239f, result.caloriesPer100());
        assertEquals(96f, result.caloriesPerServing());
        assertEquals(40f, result.servingSize());
        assertEquals(FoodEntity.Unit.G, result.servingUnit());
    }

    // -- parseMarkdownTable: dash placeholder for empty cells --

    @Test
    void parseMarkdownTable_handlesDashAsEmptyCell() {
        var markdown = """
                | Typical Values | Per 100g | Per 40g |
                | --- | --- | --- |
                | Energy | 239 kcal | 96 kcal |
                | Fat | 4.1g | - |
                | Carbohydrate | - | 24.7g |
                | Protein | 12.2g | - |
                """;
        // Dashes are not empty, but they don't parse as valid numbers.
        // valueAt returns the dash (non-blank), but parseNumber returns null for it.
        var result = parser.parseMarkdownTable(markdown, new LinkedHashMap<>());
        assertEquals(239f, result.caloriesPer100());
        assertEquals(96f, result.caloriesPerServing());
        assertEquals(4.1f, result.fatPer100());
        assertNull(result.fatPerServing());
        assertNull(result.carbsPer100());
        assertEquals(24.7f, result.carbsPerServing());
        assertEquals(12.2f, result.proteinPer100());
        assertNull(result.proteinPerServing());
    }

    // -- parseMarkdownTable: per bar serving type --

    @Test
    void parseMarkdownTable_detectsPerBarServingColumn() {
        var markdown = """
                | Typical Values | Per 100g | Per bar (50g) |
                | --- | --- | --- |
                | Energy | 239 kcal | 120 kcal |
                | Fat | 4.1g | 2.0g |
                | Carbs | 61.7g | 30.9g |
                | Protein | 12.2g | 6.1g |
                """;
        var result = parser.parseMarkdownTable(markdown, new LinkedHashMap<>());
        assertEquals(120f, result.caloriesPerServing());
        assertEquals(50f, result.servingSize());
    }

    // -- parseMarkdownTable: per slice serving type --

    @Test
    void parseMarkdownTable_detectsPerSliceServingColumn() {
        var markdown = """
                | Typical Values | Per 100g | Per slice (20g) |
                | --- | --- | --- |
                | Energy | 239 kcal | 48 kcal |
                | Fat | 4.1g | 0.8g |
                | Carbs | 61.7g | 12.3g |
                | Protein | 12.2g | 2.4g |
                """;
        var result = parser.parseMarkdownTable(markdown, new LinkedHashMap<>());
        assertEquals(48f, result.caloriesPerServing());
        assertEquals(20f, result.servingSize());
    }

    // -- parseMarkdownTable: no serving size number in header --

    @Test
    void parseMarkdownTable_handlesServingHeaderWithoutExplicitSize() {
        var markdown = """
                | Typical Values | Per 100g | Per serving |
                | --- | --- | --- |
                | Energy | 239 kcal | 96 kcal |
                | Fat | 4.1g | 1.6g |
                | Carbs | 61.7g | 24.7g |
                | Protein | 12.2g | 4.9g |
                """;
        var result = parser.parseMarkdownTable(markdown, new LinkedHashMap<>());
        assertEquals(96f, result.caloriesPerServing());
        assertNull(result.servingSize());
        assertNull(result.servingUnit());
    }

    // -- parseMarkdownTable: comma decimal values --

    @Test
    void parseMarkdownTable_parsesCommaDecimalValues() {
        var markdown = """
                | Typical Values | Per 100g | Per 40g |
                | --- | --- | --- |
                | Energy | 1000 kJ / 239 kcal | 400 kJ / 95,5 kcal |
                | Fat | 4,1g | 1,6g |
                | Carbs | 61,7g | 24,7g |
                | Protein | 12,2g | 4,9g |
                """;
        var result = parser.parseMarkdownTable(markdown, new LinkedHashMap<>());
        assertEquals(239f, result.caloriesPer100());
        assertEquals(95.5f, result.caloriesPerServing());
        assertEquals(4.1f, result.fatPer100());
    }

    // -- parseMarkdownTable: multiple data rows with same label (first wins) --

    @Test
    void parseMarkdownTable_usesFirstValueForDuplicateLabel() {
        var markdown = """
                | Typical Values | Per 100g |
                | --- | --- |
                | Energy | 1000 kJ / 239 kcal |
                | Energy | 2000 kJ / 478 kcal |
                | Fat | 4.1g |
                | Carbs | 61.7g |
                """;
        var result = parser.parseMarkdownTable(markdown, new LinkedHashMap<>());
        assertEquals(239f, result.caloriesPer100());
    }

    // -- parseMarkdownTable: header row not at first line --

    @Test
    void parseMarkdownTable_findsHeaderInLaterRow() {
        var markdown = """
                | Some | junk | header |
                | --- | --- | --- |
                | Typical Values | Per 100g | Per 40g |
                | --- | --- | --- |
                | Energy | 1000 kJ / 239 kcal | 400 kJ / 96 kcal |
                | Fat | 4.1g | 1.6g |
                | Carbs | 61.7g | 24.7g |
                | Protein | 12.2g | 4.9g |
                """;
        var result = parser.parseMarkdownTable(markdown, new LinkedHashMap<>());
        assertEquals(239f, result.caloriesPer100());
        assertEquals(96f, result.caloriesPerServing());
        assertEquals(40f, result.servingSize());
    }

    // -- parseNutritionFromTables: null/empty nodes --

    @Test
    void parseNutritionFromTables_throwsNpeForNullNode() {
        assertThrows(NullPointerException.class,
                () -> parser.parseNutritionFromTables(null, new LinkedHashMap<>()));
    }

    @Test
    void parseNutritionFromTables_returnsEmptyForEmptyArrayNode() {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var array = mapper.createArrayNode();
        var result = parser.parseNutritionFromTables(array, new LinkedHashMap<>());
        assertEquals(ParsedNutrition.empty(), result);
    }

    @Test
    void parseNutritionFromTables_returnsEmptyForBlankHtml() {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var array = mapper.createArrayNode();
        var table = mapper.createObjectNode();
        table.put("content", "   ");
        array.add(table);
        var result = parser.parseNutritionFromTables(array, new LinkedHashMap<>());
        assertEquals(ParsedNutrition.empty(), result);
    }

    @Test
    void parseNutritionFromTables_returnsEmptyForNonTableHtml() {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var array = mapper.createArrayNode();
        var table = mapper.createObjectNode();
        table.put("content", "<div>not a table</div>");
        array.add(table);
        var result = parser.parseNutritionFromTables(array, new LinkedHashMap<>());
        assertEquals(ParsedNutrition.empty(), result);
    }

    // -- parseNutritionFromTables: HTML table parsing --

    @Test
    void parseNutritionFromTables_parsesSimpleHtmlTable() {
        var html = """
                <table>
                <tr><td>Typical Values</td><td>Per 100g</td><td>Per 40g</td></tr>
                <tr><td>Energy</td><td>239 kcal</td><td>96 kcal</td></tr>
                <tr><td>Fat</td><td>4.1g</td><td>1.6g</td></tr>
                <tr><td>Carbs</td><td>61.7g</td><td>24.7g</td></tr>
                <tr><td>Protein</td><td>12.2g</td><td>4.9g</td></tr>
                </table>
                """;
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var array = mapper.createArrayNode();
        var table = mapper.createObjectNode();
        table.put("content", html);
        array.add(table);
        var result = parser.parseNutritionFromTables(array, new LinkedHashMap<>());
        assertEquals(239f, result.caloriesPer100());
        assertEquals(96f, result.caloriesPerServing());
        assertEquals(40f, result.servingSize());
    }

    @Test
    void parseNutritionFromTables_triesMultipleTables() {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var array = mapper.createArrayNode();

        var badTable = mapper.createObjectNode();
        badTable.put("content", "<table><tr><td>No nutrition data</td></tr></table>");
        array.add(badTable);

        var goodTable = mapper.createObjectNode();
        goodTable.put("content", """
                <table>
                <tr><td>Typical Values</td><td>Per 100g</td></tr>
                <tr><td>Energy</td><td>239 kcal</td></tr>
                <tr><td>Fat</td><td>4.1g</td></tr>
                <tr><td>Carbs</td><td>61.7g</td></tr>
                <tr><td>Protein</td><td>12.2g</td></tr>
                </table>
                """);
        array.add(goodTable);

        var result = parser.parseNutritionFromTables(array, new LinkedHashMap<>());
        assertEquals(239f, result.caloriesPer100());
    }

    // -- parseMarkdownTable: full extraction per 100 + per serving --

    @Test
    void parseMarkdownTable_extractsAllNutrientsWithPer100AndServing() {
        var markdown = """
                | Typical Values | Per 100g | Per 40g |
                | --- | --- | --- |
                | Energy | 1000 kJ / 239 kcal | 400 kJ / 96 kcal |
                | Fat | 4.1g | 1.6g |
                | Carbohydrate | 61.7g | 24.7g |
                | Protein | 12.2g | 4.9g |
                """;
        var raw = new LinkedHashMap<String, RawNutrient>();
        var result = parser.parseMarkdownTable(markdown, raw);
        assertEquals(239f, result.caloriesPer100());
        assertEquals(96f, result.caloriesPerServing());
        assertEquals(4.1f, result.fatPer100());
        assertEquals(1.6f, result.fatPerServing());
        assertEquals(61.7f, result.carbsPer100());
        assertEquals(24.7f, result.carbsPerServing());
        assertEquals(12.2f, result.proteinPer100());
        assertEquals(4.9f, result.proteinPerServing());
        assertEquals(40f, result.servingSize());
        assertEquals(FoodEntity.Unit.G, result.servingUnit());
    }
}

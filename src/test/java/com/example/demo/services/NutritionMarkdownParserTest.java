package com.example.demo.services;

import com.example.demo.entities.FoodEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class NutritionMarkdownParserTest {

    private final NutritionTableParser tableParser = new NutritionTableParser();
    private NutritionMarkdownParser parser;

    @BeforeEach
    void setUp() {
        parser = new NutritionMarkdownParser(tableParser);
    }

    // -- toFloat --

    @Test
    void toFloat_throwsNpeForNull() {
        assertThrows(NullPointerException.class, () -> NutritionMarkdownParser.toFloat(null));
    }

    @Test
    void toFloat_returnsNullForInvalidInput() {
        assertNull(NutritionMarkdownParser.toFloat("abc"));
    }

    @Test
    void toFloat_returnsNullForMultipleCommas() {
        assertNull(NutritionMarkdownParser.toFloat("12,5,5"));
    }

    @Test
    void toFloat_parsesInteger() {
        assertEquals(42f, NutritionMarkdownParser.toFloat("42"));
    }

    @Test
    void toFloat_parsesDecimal() {
        assertEquals(12.5f, NutritionMarkdownParser.toFloat("12.5"));
    }

    @Test
    void toFloat_parsesCommaDecimal() {
        assertEquals(12.5f, NutritionMarkdownParser.toFloat("12,5"));
    }

    @Test
    void toFloat_returnsNullForEmptyString() {
        assertNull(NutritionMarkdownParser.toFloat(""));
    }

    @Test
    void toFloat_parsesScientificNotation() {
        assertEquals(100.0f, NutritionMarkdownParser.toFloat("1e2"));
    }

    @Test
    void toFloat_parsesNegativeValue() {
        assertEquals(-12.5f, NutritionMarkdownParser.toFloat("-12.5"));
    }

    @Test
    void toFloat_parsesLeadingPlus() {
        assertEquals(12.5f, NutritionMarkdownParser.toFloat("+12.5"));
    }

    @Test
    void toFloat_returnsNullForUnicodeDigits() {
        assertNull(NutritionMarkdownParser.toFloat("١٢٫٥"));
    }

    // -- parseNumber --

    @Test
    void parseNumber_returnsNullForNull() {
        assertNull(NutritionMarkdownParser.parseNumber(null));
    }

    @Test
    void parseNumber_returnsNullForBlank() {
        assertNull(NutritionMarkdownParser.parseNumber("   "));
    }

    @Test
    void parseNumber_returnsNullForNoMatch() {
        assertNull(NutritionMarkdownParser.parseNumber("no digits here"));
    }

    @Test
    void parseNumber_parsesInteger() {
        assertEquals(42f, NutritionMarkdownParser.parseNumber("42"));
    }

    @Test
    void parseNumber_parsesDecimalWithUnit() {
        assertEquals(12.5f, NutritionMarkdownParser.parseNumber("12.5g protein"));
    }

    @Test
    void parseNumber_parsesCommaDecimal() {
        assertEquals(12.5f, NutritionMarkdownParser.parseNumber("12,5g"));
    }

    @Test
    void parseNumber_ignoresLeadingMinus() {
        assertEquals(12.5f, NutritionMarkdownParser.parseNumber("-12.5g"));
    }

    @Test
    void parseNumber_returnsFirstMatch() {
        assertEquals(12.5f, NutritionMarkdownParser.parseNumber("12.5g protein, 4.1g fat"));
    }

    @Test
    void parseNumber_parsesLeadingDigitInScientificNotation() {
        assertEquals(1.0f, NutritionMarkdownParser.parseNumber("1e2g protein"));
    }

    @Test
    void parseNumber_returnsNullForUnicodeDigits() {
        assertNull(NutritionMarkdownParser.parseNumber("١٢٫٥g"));
    }

    // -- parseEnergyKcal --

    @Test
    void parseEnergyKcal_returnsNullForNull() {
        assertNull(NutritionMarkdownParser.parseEnergyKcal(null));
    }

    @Test
    void parseEnergyKcal_returnsNullForBlank() {
        assertNull(NutritionMarkdownParser.parseEnergyKcal("   "));
    }

    @Test
    void parseEnergyKcal_returnsNullForNoPattern() {
        assertNull(NutritionMarkdownParser.parseEnergyKcal("no energy data"));
    }

    @Test
    void parseEnergyKcal_parsesKjAndKcalFormat() {
        assertEquals(239f, NutritionMarkdownParser.parseEnergyKcal("1000 kJ / 239 kcal"));
    }

    @Test
    void parseEnergyKcal_parsesKcalOnlyFormat() {
        assertEquals(239f, NutritionMarkdownParser.parseEnergyKcal("239 kcal"));
    }

    @Test
    void parseEnergyKcal_isCaseInsensitive() {
        assertEquals(239f, NutritionMarkdownParser.parseEnergyKcal("239 KCAL"));
    }

    @Test
    void parseEnergyKcal_parsesCompactFormat() {
        assertEquals(239f, NutritionMarkdownParser.parseEnergyKcal("1000kJ/239kcal"));
    }

    @Test
    void parseEnergyKcal_parsesDecimalValues() {
        assertEquals(239.5f, NutritionMarkdownParser.parseEnergyKcal("1000.5 kJ / 239.5 kcal"));
    }

    @Test
    void parseEnergyKcal_returnsNullForKjOnly() {
        assertNull(NutritionMarkdownParser.parseEnergyKcal("1000 kJ"));
    }

    @Test
    void parseEnergyKcal_reversedKjKcalOrder_usesKcalOnlyFallback() {
        assertEquals(239f, NutritionMarkdownParser.parseEnergyKcal("239 kcal / 1000 kJ"));
    }

    @Test
    void parseEnergyKcal_parsesCommaDecimal() {
        assertEquals(239.5f, NutritionMarkdownParser.parseEnergyKcal("1000 kJ / 239,5 kcal"));
    }

    @Test
    void parseEnergyKcal_parsesVeryLargeValues() {
        assertEquals(239000f, NutritionMarkdownParser.parseEnergyKcal("1000000 kJ / 239000 kcal"));
    }

    @Test
    void parseEnergyKcal_nonBreakingSpaceBeforeUnit_returnsNull() {
        assertNull(NutritionMarkdownParser.parseEnergyKcal("200\u00A0kcal"));
    }

    @Test
    void parseEnergyKcal_nonBreakingSpaceInKjKcalFormat_returnsNull() {
        assertNull(NutritionMarkdownParser.parseEnergyKcal("837 kJ / 200\u00A0kcal"));
    }

    @Test
    void parseEnergyKcal_unicodeDigits_returnsNull() {
        assertNull(NutritionMarkdownParser.parseEnergyKcal("\u0662\u0660\u0660 kcal"));
    }

    // -- putIfText --

    @Test
    void putIfText_doesNotPutNullText() {
        var map = new LinkedHashMap<String, RawNutrient>();
        NutritionMarkdownParser.putIfText(map, "KEY", null);
        assertTrue(map.isEmpty());
    }

    @Test
    void putIfText_doesNotPutBlankText() {
        var map = new LinkedHashMap<String, RawNutrient>();
        NutritionMarkdownParser.putIfText(map, "KEY", "   ");
        assertTrue(map.isEmpty());
    }

    @Test
    void putIfText_putsNonBlankText() {
        var map = new LinkedHashMap<String, RawNutrient>();
        NutritionMarkdownParser.putIfText(map, "KEY", "value");
        assertEquals("value", map.get("KEY").text());
    }

    @Test
    void putIfText_doesNotOverwriteExistingKey() {
        var map = new LinkedHashMap<String, RawNutrient>();
        map.put("KEY", new RawNutrient("existing"));
        NutritionMarkdownParser.putIfText(map, "KEY", "new");
        assertEquals("existing", map.get("KEY").text());
    }

    // -- parseServingSizeFromMarkdown --

    @Test
    void parseServingSizeFromMarkdown_throwsNpeForNull() {
        assertThrows(NullPointerException.class, () -> parser.parseServingSizeFromMarkdown(null));
    }

    @Test
    void parseServingSizeFromMarkdown_returnsNullForEmpty() {
        var result = parser.parseServingSizeFromMarkdown("");
        assertNull(result);
    }

    @Test
    void parseServingSizeFromMarkdown_parsesParenthesizedGrams() {
        var result = parser.parseServingSizeFromMarkdown("Nutrition per serving (40g)");
        assertNotNull(result);
        assertEquals(40f, result.normalizedValue());
        assertEquals(FoodEntity.Unit.G, result.unit());
        assertEquals("per serving (40g", result.rawText());
    }

    @Test
    void parseServingSizeFromMarkdown_parsesParenthesizedMl() {
        var result = parser.parseServingSizeFromMarkdown("Nutrition per portion (200ml)");
        assertNotNull(result);
        assertEquals(200f, result.normalizedValue());
        assertEquals(FoodEntity.Unit.ML, result.unit());
    }

    @Test
    void parseServingSizeFromMarkdown_parsesInlineGrams() {
        var result = parser.parseServingSizeFromMarkdown("Nutrition per serving 40g");
        assertNotNull(result);
        assertEquals(40f, result.normalizedValue());
        assertEquals(FoodEntity.Unit.G, result.unit());
        assertEquals("per serving 40g", result.rawText());
    }

    @Test
    void parseServingSizeFromMarkdown_parsesInlineMl() {
        var result = parser.parseServingSizeFromMarkdown("Nutrition per serving 200ml");
        assertNotNull(result);
        assertEquals(200f, result.normalizedValue());
        assertEquals(FoodEntity.Unit.ML, result.unit());
    }

    @Test
    void parseServingSizeFromMarkdown_parsesGenericWithContextCue() {
        var result = parser.parseServingSizeFromMarkdown("This pack contains 4 servings of 30g");
        assertNotNull(result);
        assertEquals(30f, result.normalizedValue());
        assertEquals(FoodEntity.Unit.G, result.unit());
    }

    @Test
    void parseServingSizeFromMarkdown_parsesGenericWithPerKeyword() {
        var result = parser.parseServingSizeFromMarkdown("per 200ml");
        assertNotNull(result);
        assertEquals(200f, result.normalizedValue());
        assertEquals(FoodEntity.Unit.ML, result.unit());
    }

    @Test
    void parseServingSizeFromMarkdown_skipsPer100Reference() {
        var result = parser.parseServingSizeFromMarkdown("Typical values per 100g");
        assertNull(result);
    }

    @Test
    void parseServingSizeFromMarkdown_skipsNutritionContext() {
        var result = parser.parseServingSizeFromMarkdown("Energy 239 kcal per 100g Fat 4.1g Protein 12.5g");
        assertNull(result);
    }

    @Test
    void parseServingSizeFromMarkdown_returnsNullWithoutServingCue() {
        var result = parser.parseServingSizeFromMarkdown("Total net weight 30g");
        assertNull(result);
    }

    @Test
    void parseServingSizeFromMarkdown_nonBreakingSpaceInParenthesized_returnsNull() {
        var result = parser.parseServingSizeFromMarkdown("per serving (40\u00A0g)");
        assertNull(result);
    }

    @Test
    void parseServingSizeFromMarkdown_nonBreakingSpaceInInlineServing_returnsNull() {
        var result = parser.parseServingSizeFromMarkdown("per serving 200\u00A0ml");
        assertNull(result);
    }

    @Test
    void parseServingSizeFromMarkdown_skipsPer100WithNonBreakingSpace() {
        var result = parser.parseServingSizeFromMarkdown("per\u00A0100\u00A0g");
        assertNull(result);
    }

    // -- parseMetricFromMarkdown --

    @Test
    void parseMetricFromMarkdown_returnsNullForMissingSection() {
        var markdown = "No nutrition information here";
        var result = parser.parseMetricFromMarkdown(markdown, "per 100", "energy", true, new LinkedHashMap<>(), "ENERGY_KCAL_100G");
        assertNull(result);
    }

    @Test
    void parseMetricFromMarkdown_returnsNullForMissingMetricInSection() {
        var markdown = """
                Per 100g
                No energy here
                """;
        var result = parser.parseMetricFromMarkdown(markdown, "per 100", "energy", true, new LinkedHashMap<>(), "ENERGY_KCAL_100G");
        assertNull(result);
    }

    @Test
    void parseMetricFromMarkdown_parsesEnergyFromSection() {
        var markdown = """
                Per 100g
                Energy 1000 kJ / 239 kcal
                """;
        var raw = new LinkedHashMap<String, RawNutrient>();
        var result = parser.parseMetricFromMarkdown(markdown, "per 100", "energy", true, raw, "ENERGY_KCAL_100G");
        assertEquals(239f, result);
        assertTrue(raw.containsKey("ENERGY_KCAL_100G"));
    }

    @Test
    void parseMetricFromMarkdown_parsesNutrientFromSection() {
        var markdown = """
                Per 100g
                Protein 12.5g
                """;
        var result = parser.parseMetricFromMarkdown(markdown, "per 100", "protein", false, new LinkedHashMap<>(), "PROTEINS_100G");
        assertEquals(12.5f, result);
    }

    @Test
    void parseMetricFromMarkdown_usesCarbohydrateAlias() {
        var markdown = """
                Per 100g
                Carbs 30g
                """;
        var result = parser.parseMetricFromMarkdown(markdown, "per 100", "carbohydrate", false, new LinkedHashMap<>(), "CARBOHYDRATES_100G");
        assertEquals(30f, result);
    }

    // -- parseNutritionFromMarkdown --

    @Test
    void parseNutritionFromMarkdown_returnsEmptyForNull() {
        var result = parser.parseNutritionFromMarkdown(null, new LinkedHashMap<>());
        assertEquals(ParsedNutrition.empty(), result);
    }

    @Test
    void parseNutritionFromMarkdown_returnsEmptyForBlank() {
        var result = parser.parseNutritionFromMarkdown("   ", new LinkedHashMap<>());
        assertEquals(ParsedNutrition.empty(), result);
    }

    @Test
    void parseNutritionFromMarkdown_parsesFromTableMarkdown() {
        var markdown = """
                | Typical Values | Per 100g | Per 40g |
                | --- | --- | --- |
                | Energy | 1000 kJ / 239 kcal | 400 kJ / 96 kcal |
                | Fat | 4.1g | 1.6g |
                | Carbohydrate | 61.7g | 24.7g |
                | Protein | 12.2g | 4.9g |
                """;
        var result = parser.parseNutritionFromMarkdown(markdown, new LinkedHashMap<>());
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

    @Test
    void parseNutritionFromMarkdown_parsesFromSectionWithPer100() {
        var markdown = """
                Nutrition Information
                Per 100g: Energy 1000 kJ / 239 kcal, Fat 4.1g, Carbohydrate 61.7g, Protein 12.2g
                """;
        var raw = new LinkedHashMap<String, RawNutrient>();
        var result = parser.parseNutritionFromMarkdown(markdown, raw);
        assertEquals(239f, result.caloriesPer100());
        assertEquals(4.1f, result.fatPer100());
        assertEquals(61.7f, result.carbsPer100());
        assertEquals(12.2f, result.proteinPer100());
        assertNull(result.caloriesPerServing());
        assertNull(result.servingSize());
    }

    @Test
    void parseNutritionFromMarkdown_parsesWithStandalonePerServing() {
        var markdown = """
                Nutrition Information
                Per 100g: Energy 1000 kJ / 239 kcal, Fat 4.1g, Carbohydrate 61.7g, Protein 12.2g
                Per serving 40g: Energy 400 kJ / 96 kcal, Fat 1.6g, Carbohydrate 24.7g, Protein 4.9g
                """;
        var raw = new LinkedHashMap<String, RawNutrient>();
        var result = parser.parseNutritionFromMarkdown(markdown, raw);
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
        assertTrue(raw.containsKey("SERVING_SIZE"));
    }

    @Test
    void parseNutritionFromMarkdown_nonBreakingSpaceInEnergyValue_returnsNullForCalories() {
        var markdown = "Per 100g\nEnergy 1000 kJ / 200\u00A0kcal";
        var result = parser.parseNutritionFromMarkdown(markdown, new LinkedHashMap<>());
        assertNull(result.caloriesPer100());
        assertNull(result.servingSize());
    }

    @Test
    void parseNutritionFromMarkdown_unicodeDigitsInEnergy_returnsNull() {
        var markdown = "Per 100g\nEnergy \u0662\u0660\u0660 kcal";
        var result = parser.parseNutritionFromMarkdown(markdown, new LinkedHashMap<>());
        assertNull(result.caloriesPer100());
        assertNull(result.servingSize());
    }
}

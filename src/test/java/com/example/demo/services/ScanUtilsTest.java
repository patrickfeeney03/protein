package com.example.demo.services;

import com.example.demo.entities.FoodEntity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ScanUtilsTest {

    // -- hasParsedNutrition --

    @Test
    void hasParsedNutrition_returnsFalseForNull() {
        assertFalse(ScanUtils.hasParsedNutrition(null));
    }

    @Test
    void hasParsedNutrition_returnsFalseForEmpty() {
        assertFalse(ScanUtils.hasParsedNutrition(ParsedNutrition.empty()));
    }

    @Test
    void hasParsedNutrition_returnsTrueWhenServingSizeSet() {
        var n = new ParsedNutrition(100f, FoodEntity.Unit.G, null, null, null, null, null, null, null, null);
        assertTrue(ScanUtils.hasParsedNutrition(n));
    }

    @Test
    void hasParsedNutrition_returnsTrueWhenCaloriesPerServingSet() {
        var n = new ParsedNutrition(null, null, 200f, null, null, null, null, null, null, null);
        assertTrue(ScanUtils.hasParsedNutrition(n));
    }

    @Test
    void hasParsedNutrition_returnsTrueWhenCaloriesPer100Set() {
        var n = new ParsedNutrition(null, null, null, 250f, null, null, null, null, null, null);
        assertTrue(ScanUtils.hasParsedNutrition(n));
    }

    @Test
    void hasParsedNutrition_returnsTrueWhenProteinPerServingSet() {
        var n = new ParsedNutrition(null, null, null, null, 5f, null, null, null, null, null);
        assertTrue(ScanUtils.hasParsedNutrition(n));
    }

    @Test
    void hasParsedNutrition_returnsTrueWhenProteinPer100Set() {
        var n = new ParsedNutrition(null, null, null, null, null, 10f, null, null, null, null);
        assertTrue(ScanUtils.hasParsedNutrition(n));
    }

    @Test
    void hasParsedNutrition_returnsTrueWhenCarbsPerServingSet() {
        var n = new ParsedNutrition(null, null, null, null, null, null, 20f, null, null, null);
        assertTrue(ScanUtils.hasParsedNutrition(n));
    }

    @Test
    void hasParsedNutrition_returnsTrueWhenCarbsPer100Set() {
        var n = new ParsedNutrition(null, null, null, null, null, null, null, 30f, null, null);
        assertTrue(ScanUtils.hasParsedNutrition(n));
    }

    @Test
    void hasParsedNutrition_returnsTrueWhenFatPerServingSet() {
        var n = new ParsedNutrition(null, null, null, null, null, null, null, null, 8f, null);
        assertTrue(ScanUtils.hasParsedNutrition(n));
    }

    @Test
    void hasParsedNutrition_returnsTrueWhenFatPer100Set() {
        var n = new ParsedNutrition(null, null, null, null, null, null, null, null, null, 12f);
        assertTrue(ScanUtils.hasParsedNutrition(n));
    }

    // -- hasProductValues --

    @Test
    void hasProductValues_returnsFalseForNull() {
        assertFalse(ScanUtils.hasProductValues(null));
    }

    @Test
    void hasProductValues_returnsFalseForEmpty() {
        assertFalse(ScanUtils.hasProductValues(ProductDetails.empty()));
    }

    @Test
    void hasProductValues_returnsTrueWhenNameSet() {
        var p = new ProductDetails("Coke", null, null, null, null, null, null, null, null);
        assertTrue(ScanUtils.hasProductValues(p));
    }

    @Test
    void hasProductValues_returnsTrueWhenBrandSet() {
        var p = new ProductDetails(null, "Coca-Cola", null, null, null, null, null, null, null);
        assertTrue(ScanUtils.hasProductValues(p));
    }

    @Test
    void hasProductValues_returnsTrueWhenBarcodeSet() {
        var p = new ProductDetails(null, null, "12345678", null, null, null, null, null, null);
        assertTrue(ScanUtils.hasProductValues(p));
    }

    @Test
    void hasProductValues_returnsTrueWhenStoreNameSet() {
        var p = new ProductDetails(null, null, null, "Aldi", null, null, null, null, null);
        assertTrue(ScanUtils.hasProductValues(p));
    }

    @Test
    void hasProductValues_returnsTrueWhenServingsSet() {
        var p = new ProductDetails(null, null, null, null, 4f, null, null, null, null);
        assertTrue(ScanUtils.hasProductValues(p));
    }

    @Test
    void hasProductValues_returnsTrueWhenTotalWeightSet() {
        var p = new ProductDetails(null, null, null, null, null, 500f, null, null, null);
        assertTrue(ScanUtils.hasProductValues(p));
    }

    @Test
    void hasProductValues_returnsTrueWhenTotalWeightUnitSet() {
        var p = new ProductDetails(null, null, null, null, null, null, FoodEntity.Unit.G, null, null);
        assertTrue(ScanUtils.hasProductValues(p));
    }

    @Test
    void hasProductValues_returnsTrueWhenDrainedWeightSet() {
        var p = new ProductDetails(null, null, null, null, null, null, null, 200f, null);
        assertTrue(ScanUtils.hasProductValues(p));
    }

    @Test
    void hasProductValues_returnsTrueWhenDrainedWeightUnitSet() {
        var p = new ProductDetails(null, null, null, null, null, null, null, null, FoodEntity.Unit.ML);
        assertTrue(ScanUtils.hasProductValues(p));
    }

    @Test
    void hasProductValues_returnsTrueWhenAllFieldsSet() {
        var p = new ProductDetails("Name", "Brand", "12345678", "Store", 4f, 500f, FoodEntity.Unit.G, 200f, FoodEntity.Unit.ML);
        assertTrue(ScanUtils.hasProductValues(p));
    }

    // -- normalizeText --

    @Test
    void normalizeText_returnsEmptyForNull() {
        assertEquals("", ScanUtils.normalizeText(null));
    }

    @Test
    void normalizeText_returnsEmptyForEmpty() {
        assertEquals("", ScanUtils.normalizeText(""));
    }

    @Test
    void normalizeText_lowercases() {
        assertEquals("hello world", ScanUtils.normalizeText("Hello World"));
    }

    @Test
    void normalizeText_removesSpecialChars() {
        assertEquals("hello world", ScanUtils.normalizeText("Hello, World!"));
    }

    @Test
    void normalizeText_collapsesSpaces() {
        assertEquals("a b c", ScanUtils.normalizeText("a   b   c"));
    }

    @Test
    void normalizeText_trimsWhitespace() {
        assertEquals("hello", ScanUtils.normalizeText("  hello  "));
    }

    @Test
    void normalizeText_handlesMixedInput() {
        assertEquals("coca cola 500ml", ScanUtils.normalizeText("Coca-Cola 500mL!"));
    }

    @Test
    void normalizeText_stripsAccentedCharacters() {
        assertEquals("b arnaise", ScanUtils.normalizeText("Béarnaise"));
    }

    @Test
    void normalizeText_stripsUmlautCharacters() {
        assertEquals("m sli", ScanUtils.normalizeText("Müsli"));
    }

    @Test
    void normalizeText_stripsTildeCharacters() {
        assertEquals("pi ata", ScanUtils.normalizeText("Piñata"));
    }

    @Test
    void normalizeText_stripsCedillaCharacters() {
        assertEquals("fran ais", ScanUtils.normalizeText("Français"));
    }

    @Test
    void normalizeText_convertsNonBreakingSpaceToSpace() {
        assertEquals("hello world", ScanUtils.normalizeText("hello\u00A0world"));
    }

    // -- normalizeTextLenient --

    @Test
    void normalizeTextLenient_returnsEmptyForNull() {
        assertEquals("", ScanUtils.normalizeTextLenient(null));
    }

    @Test
    void normalizeTextLenient_returnsEmptyForEmpty() {
        assertEquals("", ScanUtils.normalizeTextLenient(""));
    }

    @Test
    void normalizeTextLenient_lowercases() {
        assertEquals("hello world", ScanUtils.normalizeTextLenient("Hello World"));
    }

    @Test
    void normalizeTextLenient_collapsesSpaces() {
        assertEquals("a b c", ScanUtils.normalizeTextLenient("a   b   c"));
    }

    @Test
    void normalizeTextLenient_trimsWhitespace() {
        assertEquals("hello", ScanUtils.normalizeTextLenient("  hello  "));
    }

    @Test
    void normalizeTextLenient_preservesAccentedCharacters() {
        assertEquals("béarnaise", ScanUtils.normalizeTextLenient("Béarnaise"));
    }

    @Test
    void normalizeTextLenient_preservesUmlautCharacters() {
        assertEquals("müsli", ScanUtils.normalizeTextLenient("Müsli"));
    }

    @Test
    void normalizeTextLenient_preservesTildeCharacters() {
        assertEquals("piñata", ScanUtils.normalizeTextLenient("Piñata"));
    }

    @Test
    void normalizeTextLenient_preservesCedillaCharacters() {
        assertEquals("français", ScanUtils.normalizeTextLenient("Français"));
    }

    @Test
    void normalizeTextLenient_stripsSymbols() {
        assertEquals("hello world", ScanUtils.normalizeTextLenient("Hello, World!"));
    }

    @Test
    void normalizeTextLenient_convertsNonBreakingSpaceToSpace() {
        assertEquals("hello world", ScanUtils.normalizeTextLenient("hello\u00A0world"));
    }

    @Test
    void normalizeTextLenient_preservesUnicodeLetters() {
        assertEquals("café naïve", ScanUtils.normalizeTextLenient("Café Naïve"));
    }

    @Test
    void normalizeTextLenient_contrastWithStrict() {
        assertEquals("b arnaise", ScanUtils.normalizeText("Béarnaise"));
        assertEquals("béarnaise", ScanUtils.normalizeTextLenient("Béarnaise"));
    }

    // -- firstNonNull --

    @Test
    void firstNonNull_returnsNullWhenBothNull() {
        assertNull(ScanUtils.firstNonNull(null, null));
    }

    @Test
    void firstNonNull_returnsSecondWhenFirstNull() {
        assertEquals("b", ScanUtils.firstNonNull(null, "b"));
    }

    @Test
    void firstNonNull_returnsFirstWhenSecondNull() {
        assertEquals("a", ScanUtils.firstNonNull("a", null));
    }

    @Test
    void firstNonNull_returnsFirstWhenBothNonNull() {
        assertEquals("a", ScanUtils.firstNonNull("a", "b"));
    }

    @Test
    void firstNonNull_worksWithNumbers() {
        assertEquals(Integer.valueOf(42), ScanUtils.firstNonNull(42, 99));
    }

    @Test
    void firstNonNull_worksWithFloats() {
        assertEquals(3.14f, ScanUtils.firstNonNull(3.14f, 2.71f));
    }

    // -- hasMissingNutritionFields --

    @Test
    void hasMissingNutritionFields_returnsTrueForNull() {
        assertTrue(ScanUtils.hasMissingNutritionFields(null));
    }

    @Test
    void hasMissingNutritionFields_returnsTrueForEmpty() {
        assertTrue(ScanUtils.hasMissingNutritionFields(ParsedNutrition.empty()));
    }

    @Test
    void hasMissingNutritionFields_returnsFalseWhenAllFieldsSet() {
        var n = new ParsedNutrition(100f, FoodEntity.Unit.G, 200f, 250f, 5f, 10f, 20f, 30f, 8f, 12f);
        assertFalse(ScanUtils.hasMissingNutritionFields(n));
    }

    @Test
    void hasMissingNutritionFields_returnsTrueWhenServingSizeNull() {
        var n = new ParsedNutrition(null, FoodEntity.Unit.G, 200f, 250f, 5f, 10f, 20f, 30f, 8f, 12f);
        assertTrue(ScanUtils.hasMissingNutritionFields(n));
    }

    @Test
    void hasMissingNutritionFields_returnsTrueWhenServingUnitNull() {
        var n = new ParsedNutrition(100f, null, 200f, 250f, 5f, 10f, 20f, 30f, 8f, 12f);
        assertTrue(ScanUtils.hasMissingNutritionFields(n));
    }

    @Test
    void hasMissingNutritionFields_returnsTrueWhenCaloriesPerServingNull() {
        var n = new ParsedNutrition(100f, FoodEntity.Unit.G, null, 250f, 5f, 10f, 20f, 30f, 8f, 12f);
        assertTrue(ScanUtils.hasMissingNutritionFields(n));
    }

    @Test
    void hasMissingNutritionFields_returnsTrueWhenCaloriesPer100Null() {
        var n = new ParsedNutrition(100f, FoodEntity.Unit.G, 200f, null, 5f, 10f, 20f, 30f, 8f, 12f);
        assertTrue(ScanUtils.hasMissingNutritionFields(n));
    }

    @Test
    void hasMissingNutritionFields_returnsTrueWhenProteinPerServingNull() {
        var n = new ParsedNutrition(100f, FoodEntity.Unit.G, 200f, 250f, null, 10f, 20f, 30f, 8f, 12f);
        assertTrue(ScanUtils.hasMissingNutritionFields(n));
    }

    @Test
    void hasMissingNutritionFields_returnsTrueWhenProteinPer100Null() {
        var n = new ParsedNutrition(100f, FoodEntity.Unit.G, 200f, 250f, 5f, null, 20f, 30f, 8f, 12f);
        assertTrue(ScanUtils.hasMissingNutritionFields(n));
    }

    @Test
    void hasMissingNutritionFields_returnsTrueWhenCarbsPerServingNull() {
        var n = new ParsedNutrition(100f, FoodEntity.Unit.G, 200f, 250f, 5f, 10f, null, 30f, 8f, 12f);
        assertTrue(ScanUtils.hasMissingNutritionFields(n));
    }

    @Test
    void hasMissingNutritionFields_returnsTrueWhenCarbsPer100Null() {
        var n = new ParsedNutrition(100f, FoodEntity.Unit.G, 200f, 250f, 5f, 10f, 20f, null, 8f, 12f);
        assertTrue(ScanUtils.hasMissingNutritionFields(n));
    }

    @Test
    void hasMissingNutritionFields_returnsTrueWhenFatPerServingNull() {
        var n = new ParsedNutrition(100f, FoodEntity.Unit.G, 200f, 250f, 5f, 10f, 20f, 30f, null, 12f);
        assertTrue(ScanUtils.hasMissingNutritionFields(n));
    }

    @Test
    void hasMissingNutritionFields_returnsTrueWhenFatPer100Null() {
        var n = new ParsedNutrition(100f, FoodEntity.Unit.G, 200f, 250f, 5f, 10f, 20f, 30f, 8f, null);
        assertTrue(ScanUtils.hasMissingNutritionFields(n));
    }
}

package com.example.demo.services;

import com.example.demo.entities.FoodEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AnnotationParserTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final AnnotationParser parser = new AnnotationParser(mapper);

    // -- parse() --

    @Test
    void parse_returnsNullForNullNode() {
        assertNull(parser.parse(null, new ArrayList<>()));
    }

    @Test
    void parse_returnsNullForMissingNode() {
        assertNull(parser.parse(mapper.missingNode(), new ArrayList<>()));
    }

    @Test
    void parse_returnsNullForNullNode2() {
        assertNull(parser.parse(mapper.nullNode(), new ArrayList<>()));
    }

    @Test
    void parse_returnsObjectNodeAsIs() {
        var node = mapper.createObjectNode().put("foo", "bar");
        assertSame(node, parser.parse(node, new ArrayList<>()));
    }

    @Test
    void parse_addsWarningForNonTextualNonObjectNode() {
        var warnings = new ArrayList<String>();
        var result = parser.parse(mapper.getNodeFactory().numberNode(42), warnings);
        assertNull(result);
        assertEquals(List.of("Unexpected document annotation shape from Mistral OCR"), warnings);
    }

    @Test
    void parse_returnsNullForEmptyText() {
        assertNull(parser.parse(mapper.getNodeFactory().textNode(""), new ArrayList<>()));
    }

    @Test
    void parse_returnsNullForBlankText() {
        assertNull(parser.parse(mapper.getNodeFactory().textNode("   "), new ArrayList<>()));
    }

    @Test
    void parse_parsesValidJsonText() throws Exception {
        var json = mapper.getNodeFactory().textNode("{\"key\":\"value\"}");
        var result = parser.parse(json, new ArrayList<>());
        assertNotNull(result);
        assertTrue(result.isObject());
        assertEquals("value", result.get("key").asText());
    }

    @Test
    void parse_addsWarningForInvalidJsonText() {
        var warnings = new ArrayList<String>();
        var result = parser.parse(mapper.getNodeFactory().textNode("{invalid}"), warnings);
        assertNull(result);
        assertEquals(List.of("Failed to parse document annotation"), warnings);
    }

    // -- parseNutrition() --

    @Test
    void parseNutrition_returnsEmptyForNullNode() {
        var parsed = parser.parseNutrition(null, new LinkedHashMap<>());
        assertEquals(ParsedNutrition.empty(), parsed);
    }

    @Test
    void parseNutrition_returnsEmptyForMissingNode() {
        var parsed = parser.parseNutrition(mapper.missingNode(), new LinkedHashMap<>());
        assertEquals(ParsedNutrition.empty(), parsed);
    }

    @Test
    void parseNutrition_returnsEmptyForNullNode2() {
        var parsed = parser.parseNutrition(mapper.nullNode(), new LinkedHashMap<>());
        assertEquals(ParsedNutrition.empty(), parsed);
    }

    @Test
    void parseNutrition_extractsAllFields() {
        var macrosPerServing = mapper.createObjectNode()
                .put("energy_kcal", 142)
                .put("fat_g", 1.7)
                .put("carbohydrate_g", 24.7)
                .put("protein_g", 4.9);
        var macrosPer100 = mapper.createObjectNode()
                .put("energy_kcal", 354)
                .put("fat_g", 4.1)
                .put("carbohydrate_g", 61.7)
                .put("protein_g", 12.2);
        var annotation = mapper.createObjectNode()
                .put("serving_size", 40)
                .put("serving_unit", "g");
        annotation.set("macros_per_serving", macrosPerServing);
        annotation.set("macros_per_100", macrosPer100);

        var raw = new LinkedHashMap<String, RawNutrient>();
        var parsed = parser.parseNutrition(annotation, raw);

        assertEquals(40f, parsed.servingSize());
        assertEquals(FoodEntity.Unit.G, parsed.servingUnit());
        assertEquals(142f, parsed.caloriesPerServing());
        assertEquals(354f, parsed.caloriesPer100());
        assertEquals(4.9f, parsed.proteinPerServing());
        assertEquals(12.2f, parsed.proteinPer100());
        assertEquals(24.7f, parsed.carbsPerServing());
        assertEquals(61.7f, parsed.carbsPer100());
        assertEquals(1.7f, parsed.fatPerServing());
        assertEquals(4.1f, parsed.fatPer100());

        assertEquals("40.0 g", raw.get("SERVING_SIZE").text());
        assertEquals("142.0 kcal", raw.get("ENERGY_KCAL_SERVING").text());
        assertEquals("354.0 kcal", raw.get("ENERGY_KCAL_100G").text());
    }

    @Test
    void parseNutrition_handlesMlUnit() {
        var annotation = mapper.createObjectNode()
                .put("serving_size", 200)
                .put("serving_unit", "ml");

        var parsed = parser.parseNutrition(annotation, new LinkedHashMap<>());

        assertEquals(200f, parsed.servingSize());
        assertEquals(FoodEntity.Unit.ML, parsed.servingUnit());
    }

    @Test
    void parseNutrition_handlesNullServingUnit() {
        var annotation = mapper.createObjectNode()
                .put("serving_size", 40)
                .putNull("serving_unit");

        var parsed = parser.parseNutrition(annotation, new LinkedHashMap<>());

        assertEquals(40f, parsed.servingSize());
        assertNull(parsed.servingUnit());
    }

    @Test
    void parseNutrition_returnsNullsForMissingMacros() {
        var annotation = mapper.createObjectNode()
                .put("serving_size", 40)
                .put("serving_unit", "g");
        annotation.set("macros_per_serving", mapper.createObjectNode());
        annotation.set("macros_per_100", mapper.createObjectNode());

        var parsed = parser.parseNutrition(annotation, new LinkedHashMap<>());

        assertEquals(40f, parsed.servingSize());
        assertNull(parsed.caloriesPerServing());
        assertNull(parsed.caloriesPer100());
        assertNull(parsed.proteinPerServing());
        assertNull(parsed.proteinPer100());
    }

    @Test
    void parseNutrition_skipsRawEntryForNullValue() {
        var annotation = mapper.createObjectNode()
                .put("serving_size", 40)
                .put("serving_unit", "g")
                .set("macros_per_serving", mapper.createObjectNode()
                        .putNull("energy_kcal")
                        .put("fat_g", 1.7));

        var raw = new LinkedHashMap<String, RawNutrient>();
        parser.parseNutrition(annotation, raw);

        assertNull(raw.get("ENERGY_KCAL_SERVING"));
        assertEquals("1.7 g", raw.get("FAT_SERVING").text());
    }

    @Test
    void parseNutrition_preservesExistingRawEntries() {
        var annotation = mapper.createObjectNode()
                .put("serving_size", 40)
                .put("serving_unit", "g")
                .set("macros_per_100", mapper.createObjectNode()
                        .put("energy_kcal", 354));

        var raw = new LinkedHashMap<String, RawNutrient>();
        raw.put("ENERGY_KCAL_100G", new RawNutrient("already present"));
        parser.parseNutrition(annotation, raw);

        assertEquals("already present", raw.get("ENERGY_KCAL_100G").text());
    }

    @Test
    void parseNutrition_parsesTextualNumberFields() {
        var annotation = mapper.createObjectNode()
                .put("serving_size", 40)
                .put("serving_unit", "g")
                .set("macros_per_serving", mapper.createObjectNode()
                        .put("energy_kcal", "142.5")
                        .put("fat_g", "1,7")
                        .put("carbohydrate_g", "24.0")
                        .put("protein_g", "4.9"));

        var parsed = parser.parseNutrition(annotation, new LinkedHashMap<>());

        assertEquals(142.5f, parsed.caloriesPerServing());
        assertEquals(1.7f, parsed.fatPerServing());
        assertEquals(24f, parsed.carbsPerServing());
        assertEquals(4.9f, parsed.proteinPerServing());
    }

    // -- parseProduct() --

    @Test
    void parseProduct_returnsEmptyForNullNode() {
        var product = parser.parseProduct(null);
        assertEquals(ProductDetails.empty(), product);
    }

    @Test
    void parseProduct_returnsEmptyForMissingNode() {
        var product = parser.parseProduct(mapper.missingNode());
        assertEquals(ProductDetails.empty(), product);
    }

    @Test
    void parseProduct_returnsEmptyForNullNode2() {
        var product = parser.parseProduct(mapper.nullNode());
        assertEquals(ProductDetails.empty(), product);
    }

    @Test
    void parseProduct_extractsAllFields() {
        var annotation = mapper.createObjectNode()
                .put("name", "   Rolled  Oats   ")
                .put("brand", "  BrandName  ")
                .put("barcode_number", "1234567890123")
                .put("store_name", "aldi")
                .put("servings_per_container", 3)
                .put("total_weight", 1200)
                .put("total_weight_unit", "g");

        var product = parser.parseProduct(annotation);

        assertEquals("Rolled Oats", product.name());
        assertEquals("BrandName", product.brand());
        assertEquals("1234567890123", product.barcodeNumber());
        assertEquals("Aldi", product.storeName());
        assertEquals(3f, product.servingsPerContainer());
        assertEquals(1200f, product.totalWeight());
        assertEquals(FoodEntity.Unit.G, product.totalWeightUnit());
        assertNull(product.drainedWeight());
        assertNull(product.drainedWeightUnit());
    }

    @Test
    void parseProduct_normalizesStoreNames() {
        assertEquals("Aldi", parseStoreName("aldi"));
        assertEquals("Aldi", parseStoreName("ALDI"));
        assertEquals("Lidl", parseStoreName("lidl"));
        assertEquals("Tesco", parseStoreName("tesco"));
        assertEquals("Dunnes", parseStoreName("dunnes"));
        assertEquals("Dunnes", parseStoreName("dunnes stores"));
    }

    @Test
    void parseProduct_passesThroughUnknownStore() {
        var annotation = mapper.createObjectNode()
                .put("store_name", "SuperValu");
        assertEquals("SuperValu", parser.parseProduct(annotation).storeName());
    }

    @Test
    void parseProduct_convertsKgToG() {
        var annotation = mapper.createObjectNode()
                .put("total_weight", 1.5)
                .put("total_weight_unit", "kg");
        var product = parser.parseProduct(annotation);
        assertEquals(1500f, product.totalWeight());
        assertEquals(FoodEntity.Unit.G, product.totalWeightUnit());
    }

    @Test
    void parseProduct_convertsLToMl() {
        var annotation = mapper.createObjectNode()
                .put("total_weight", 2)
                .put("total_weight_unit", "l");
        var product = parser.parseProduct(annotation);
        assertEquals(2000f, product.totalWeight());
        assertEquals(FoodEntity.Unit.ML, product.totalWeightUnit());
    }

    @Test
    void parseProduct_handlesDrainedWeight() {
        var annotation = mapper.createObjectNode()
                .put("drained_weight", 150)
                .put("drained_weight_unit", "g");
        var product = parser.parseProduct(annotation);
        assertEquals(150f, product.drainedWeight());
        assertEquals(FoodEntity.Unit.G, product.drainedWeightUnit());
    }

    @Test
    void parseProduct_returnsNullForBlankName() {
        var annotation = mapper.createObjectNode()
                .put("name", "   ");
        assertNull(parser.parseProduct(annotation).name());
    }

    @Test
    void parseProduct_returnsNullForMissingFields() {
        var product = parser.parseProduct(mapper.createObjectNode());
        assertNull(product.name());
        assertNull(product.brand());
        assertNull(product.barcodeNumber());
        assertNull(product.storeName());
        assertNull(product.servingsPerContainer());
        assertNull(product.totalWeight());
        assertNull(product.totalWeightUnit());
    }

    // -- parse() additional edge cases --

    @Test
    void parse_jsonArrayInAnnotationText_returnsArrayNode() {
        var node = mapper.getNodeFactory().textNode("[1,2,3]");
        var result = parser.parse(node, new ArrayList<>());
        assertNotNull(result);
        assertTrue(result.isArray());
    }

    // -- parseNutrition() additional edge cases --

    @Test
    void parseNutrition_wrongJsonNodeTypes_returnsNulls() {
        var macrosPer100 = mapper.createObjectNode();
        macrosPer100.set("energy_kcal", mapper.getNodeFactory().arrayNode().add(100));
        macrosPer100.set("fat_g", mapper.createObjectNode());
        macrosPer100.set("carbohydrate_g", mapper.getNodeFactory().booleanNode(true));
        macrosPer100.set("protein_g", mapper.getNodeFactory().textNode("notANumber"));
        var annotation = mapper.createObjectNode()
                .put("serving_size", 40)
                .put("serving_unit", "g");
        annotation.set("macros_per_serving", mapper.createObjectNode()
                .put("energy_kcal", 200)
                .put("fat_g", 5));
        annotation.set("macros_per_100", macrosPer100);

        var parsed = parser.parseNutrition(annotation, new LinkedHashMap<>());

        assertEquals(40f, parsed.servingSize());
        assertEquals(200f, parsed.caloriesPerServing());
        assertEquals(5f, parsed.fatPerServing());
        assertNull(parsed.caloriesPer100());
        assertNull(parsed.fatPer100());
        assertNull(parsed.carbsPer100());
        assertNull(parsed.proteinPer100());
    }

    @Test
    void parseNutrition_servingSizeAsTextualValue() {
        var annotation = mapper.createObjectNode()
                .put("serving_size", "100")
                .put("serving_unit", "g")
                .set("macros_per_serving", mapper.createObjectNode()
                        .put("energy_kcal", 142));
        var raw = new LinkedHashMap<String, RawNutrient>();
        var parsed = parser.parseNutrition(annotation, raw);

        assertEquals(100f, parsed.servingSize());
        assertEquals("100.0 g", raw.get("SERVING_SIZE").text());
    }

    @Test
    void parseNutrition_zeroValuesPreserved() {
        var macrosPer100 = mapper.createObjectNode()
                .put("energy_kcal", 0)
                .put("fat_g", 0)
                .put("carbohydrate_g", 0)
                .put("protein_g", 0);
        var annotation = mapper.createObjectNode();
        annotation.put("serving_size", 40);
        annotation.put("serving_unit", "g");
        annotation.set("macros_per_serving", mapper.createObjectNode()
                .put("energy_kcal", 0)
                .put("fat_g", 0)
                .put("carbohydrate_g", 0)
                .put("protein_g", 0));
        annotation.set("macros_per_100", macrosPer100);

        var parsed = parser.parseNutrition(annotation, new LinkedHashMap<>());

        assertEquals(0f, parsed.caloriesPerServing());
        assertEquals(0f, parsed.caloriesPer100());
        assertEquals(0f, parsed.fatPerServing());
        assertEquals(0f, parsed.fatPer100());
        assertEquals(0f, parsed.carbsPerServing());
        assertEquals(0f, parsed.carbsPer100());
        assertEquals(0f, parsed.proteinPerServing());
        assertEquals(0f, parsed.proteinPer100());
    }

    // -- parseProduct() additional edge cases --

    @Test
    void parseProduct_totalWeightAsTextualValue() {
        var annotation = mapper.createObjectNode()
                .put("total_weight", "1200")
                .put("total_weight_unit", "g");
        var product = parser.parseProduct(annotation);
        assertEquals(1200f, product.totalWeight());
    }

    @Test
    void parseProduct_servingsPerContainerAsTextualValue() {
        var annotation = mapper.createObjectNode()
                .put("servings_per_container", "3");
        var product = parser.parseProduct(annotation);
        assertEquals(3f, product.servingsPerContainer());
    }

    @Test
    void parseProduct_barcodeAsTextualValue() {
        var annotation = mapper.createObjectNode()
                .put("barcode_number", "5012345678900");
        var product = parser.parseProduct(annotation);
        assertEquals("5012345678900", product.barcodeNumber());
    }

    @Test
    void parseProduct_barcodeAsNumericValue() {
        var annotation = mapper.createObjectNode()
                .put("barcode_number", 5012345678900L);
        var product = parser.parseProduct(annotation);
        assertEquals("5012345678900", product.barcodeNumber());
    }

    @Test
    void parseProduct_drainedWeightAsTextualValue() {
        var annotation = mapper.createObjectNode()
                .put("drained_weight", "150")
                .put("drained_weight_unit", "g");
        var product = parser.parseProduct(annotation);
        assertEquals(150f, product.drainedWeight());
        assertEquals(FoodEntity.Unit.G, product.drainedWeightUnit());
    }

    // -- Unicode / special character edge cases --

    @Test
    void parseProduct_nameWithUnicodeCharacters() {
        var annotation = mapper.createObjectNode()
                .put("name", "Røged Laks")
                .put("brand", "Schär")
                .put("store_name", "Aldi");
        var product = parser.parseProduct(annotation);
        assertEquals("Røged Laks", product.name());
        assertEquals("Schär", product.brand());
    }

    @Test
    void parseNutrition_textualNumberWithLeadingText() {
        var annotation = mapper.createObjectNode()
                .put("serving_size", 40)
                .put("serving_unit", "g")
                .set("macros_per_100", mapper.createObjectNode()
                        .put("energy_kcal", "approx 354")
                        .put("fat_g", "~4.1 g")
                        .put("carbohydrate_g", "ca. 61,7")
                        .put("protein_g", "12.2g"));
        var parsed = parser.parseNutrition(annotation, new LinkedHashMap<>());
        assertEquals(354f, parsed.caloriesPer100());
        assertEquals(4.1f, parsed.fatPer100());
        assertEquals(61.7f, parsed.carbsPer100());
        assertEquals(12.2f, parsed.proteinPer100());
    }

    @Test
    void parseProduct_storeNameWithSpecialCharacters() {
        assertEquals("Aldi", parseStoreName("Aldi!"));
        assertEquals("Aldi", parseStoreName("  ALDI  "));
        assertEquals("Dunnes", parseStoreName("Dunnes_Stores"));
        assertEquals("Tesco", parseStoreName("Tesco!!"));
        assertEquals("Lidl", parseStoreName(" lidl "));
    }

    @Test
    void parseNutrition_nonAsciiDigitsReturnNull() {
        var annotation = mapper.createObjectNode()
                .put("serving_size", 40)
                .put("serving_unit", "g")
                .set("macros_per_100", mapper.createObjectNode()
                        .put("energy_kcal", "٣٥٤")
                        .put("fat_g", "४.१"));
        var parsed = parser.parseNutrition(annotation, new LinkedHashMap<>());
        assertNull(parsed.caloriesPer100());
        assertNull(parsed.fatPer100());
    }

    @Test
    void parseProduct_nameWithNonBreakingSpace() {
        var annotation = mapper.createObjectNode()
                .put("name", "Rolled\u00A0Oats")
                .put("brand", "Brand")
                .put("store_name", "Aldi");
        var product = parser.parseProduct(annotation);
        assertEquals("Rolled\u00A0Oats", product.name());
        assertEquals("Brand", product.brand());
    }

    private String parseStoreName(String raw) {
        var annotation = mapper.createObjectNode().put("store_name", raw);
        return parser.parseProduct(annotation).storeName();
    }
}

package com.example.demo.services;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.demo.NutritionScannerProperties;
import com.example.demo.entities.FoodEntity;
import com.example.demo.services.OcrApiClient;
import com.example.demo.services.ProductDetails;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class NutritionScanServiceTest {

    private final NutritionScanService service =
            new NutritionScanService(new ObjectMapper(), new NutritionScannerProperties());

    @Test
    void parseMistralResponse_usesDocumentAnnotationWhenRawOcrHasNoValues() throws Exception {
        var response = """
                {
                  "pages": [
                    {
                      "index": 0,
                      "markdown": "# Oats"
                    }
                  ],
                  "document_annotation": "{\\"name\\":\\"Oats\\",\\"brand\\":\\"Brand\\",\\"store_name\\":\\"Aldi\\",\\"servings_per_container\\":3,\\"total_weight\\":1200,\\"total_weight_unit\\":\\"g\\",\\"serving_size\\":40,\\"serving_unit\\":\\"g\\",\\"macros_per_serving\\":{\\"energy_kcal\\":142,\\"fat_g\\":1.7,\\"carbohydrate_g\\":24.7,\\"protein_g\\":4.9},\\"macros_per_100\\":{\\"energy_kj\\":1494,\\"energy_kcal\\":354,\\"fat_g\\":4.1,\\"carbohydrate_g\\":61.7,\\"protein_g\\":12.2}}"
                }
                """;

        var result = service.parseMistralResponse(response);

        assertTrue(result.scanSucceeded());
        assertEquals(FoodEntity.Unit.G, result.parsed().servingUnit());
        assertEquals(40f, result.parsed().servingSize());
        assertEquals(142f, result.parsed().caloriesPerServing());
        assertEquals(354f, result.parsed().caloriesPer100());
        assertEquals(12.2f, result.parsed().proteinPer100());
        assertTrue(result.warnings().isEmpty());
        assertTrue(result.disagreements().isEmpty());
        assertTrue(result.productDisagreements().isEmpty());
        assertEquals(ScanSource.ANNOTATION, result.sourceUsed());
        assertFalse(result.usedAnnotationFallback());
        assertEquals(ScanSource.ANNOTATION, result.productSourceUsed());
        assertTrue(result.productUsedAnnotationFallback());
        assertEquals("Oats", result.product().name());
        assertEquals("Brand", result.product().brand());
        assertEquals("Aldi", result.product().storeName());
        assertEquals(3f, result.product().servingsPerContainer());
        assertEquals(1200f, result.product().totalWeight());
        assertEquals(FoodEntity.Unit.G, result.product().totalWeightUnit());
        assertNull(result.product().drainedWeight());
        assertNull(result.product().drainedWeightUnit());
        assertEquals(12f, result.product().getServingsPerContainer100());
        assertTrue(result.rawNutrients().isEmpty());
    }

    @Test
    void parseMistralResponse_fallsBackToTables() throws Exception {
        var response = """
                {
                  "pages": [
                    {
                      "index": 0,
                      "markdown": "NUTRITION\\n\\n[tbl-0.html](tbl-0.html)",
                      "tables": [
                        {
                          "id": "tbl-0.html",
                          "content": "<table><tr><th>TYPICAL VALUES</th><th>Per 100g</th><th>Per 1/2 can / approx. 120g</th></tr><tr><td>Energy</td><td>535kJ/127kcal</td><td>700kJ/30kcal</td></tr><tr><td>Fat</td><td>2.2g</td><td>2.8g</td></tr><tr><td>Carbohydrate</td><td>16.0g</td><td>16.0g</td></tr><tr><td>Protein</td><td>7.3g</td><td>6.9g</td></tr></table>"
                        }
                      ]
                    }
                  ],
                  "document_annotation": ""
                }
                """;

        var result = service.parseMistralResponse(response);

        assertTrue(result.scanSucceeded());
        assertEquals(FoodEntity.Unit.G, result.parsed().servingUnit());
        assertEquals(120f, result.parsed().servingSize());
        assertEquals(127f, result.parsed().caloriesPer100());
        assertEquals(30f, result.parsed().caloriesPerServing());
        assertEquals(7.3f, result.parsed().proteinPer100());
        assertEquals(ScanSource.RAW_TABLE, result.sourceUsed());
        assertFalse(result.usedAnnotationFallback());
        assertEquals(ScanSource.NONE, result.productSourceUsed());
        assertEquals("16.0g", result.rawNutrients().get("CARBOHYDRATES_100G").text());
    }

    @Test
    void parseMistralResponse_ignoresPercentColumnInNutritionTable() throws Exception {
        var response = """
                {
                  "pages": [
                    {
                      "index": 0,
                      "tables": [
                        {
                          "id": "tbl-0.html",
                          "content": "<table><tr><th>Nutrition Information</th><th>100 g</th><th>50 g</th><th>% / 50 g</th></tr><tr><td>Energy</td><td>1674kJ/399kcal</td><td>837kJ/200kcal</td><td>10%</td></tr><tr><td>Fat</td><td>14g</td><td>7.0g</td><td>10%</td></tr><tr><td>Carbohydrate</td><td>61g</td><td>31g</td><td>12%</td></tr><tr><td>Protein</td><td>6.1g</td><td>3.0g</td><td>6%</td></tr></table>"
                        }
                      ]
                    }
                  ]
                }
                """;

        var result = service.parseMistralResponse(response);

        assertTrue(result.scanSucceeded());
        assertEquals(ScanSource.RAW_TABLE, result.sourceUsed());
        assertEquals(50f, result.parsed().servingSize());
        assertEquals(200f, result.parsed().caloriesPerServing());
        assertEquals(399f, result.parsed().caloriesPer100());
        assertEquals(31f, result.parsed().carbsPerServing());
        assertEquals(61f, result.parsed().carbsPer100());
        assertEquals(3f, result.parsed().proteinPerServing());
        assertEquals(6.1f, result.parsed().proteinPer100());
        assertEquals(7f, result.parsed().fatPerServing());
        assertEquals(14f, result.parsed().fatPer100());
        assertEquals("837kJ/200kcal", result.rawNutrients().get("ENERGY_KCAL_SERVING").text());
    }

    @Test
    void parseMistralResponse_handlesTitleRowBeforeActualHeader() throws Exception {
        var response = """
                {
                  "pages": [
                    {
                      "index": 0,
                      "tables": [
                        {
                          "id": "tbl-0.html",
                          "content": "<table><tr><td colspan=\\\"3\\\">NUTRITIONAL INFORMATION</td></tr><tr><td>Typical Values</td><td>per 100g</td><td>Per 50g bar</td></tr><tr><td>Energy</td><td>1509 kJ</td><td>755 kJ</td></tr><tr><td>Energy</td><td>359 kcal</td><td>180 kcal</td></tr><tr><td>Fat</td><td>11g</td><td>6g</td></tr><tr><td>Carbohydrates</td><td>26g</td><td>13g</td></tr><tr><td>Protein</td><td>32g</td><td>16g</td></tr></table>"
                        }
                      ]
                    }
                  ],
                  "document_annotation": "{\\"macros_per_100\\":{\\"energy_kj\\":1640,\\"energy_kcal\\":392,\\"fat_g\\":16,\\"carbohydrate_g\\":38,\\"protein_g\\":25}}"
                }
                """;

        var result = service.parseMistralResponse(response);

        assertTrue(result.scanSucceeded());
        assertEquals(ScanSource.ANNOTATION, result.sourceUsed());
        assertEquals(50f, result.parsed().servingSize());
        assertEquals(180f, result.parsed().caloriesPerServing());
        assertEquals(359f, result.parsed().caloriesPer100());
        assertEquals(16f, result.parsed().proteinPerServing());
        assertEquals(32f, result.parsed().proteinPer100());
        assertEquals(13f, result.parsed().carbsPerServing());
        assertEquals(26f, result.parsed().carbsPer100());
        assertEquals(6f, result.parsed().fatPerServing());
        assertEquals(11f, result.parsed().fatPer100());
    }

    @Test
    void parseMistralResponse_parsesMarkdownPipeTableWithNormalizedLogic() throws Exception {
        var response = """
                {
                  "pages": [
                    {
                      "index": 0,
                      "markdown": "| NUTRITIONAL INFORMATION | | |\\n| --- | --- | --- |\\n| Typical Values | per 100g | Per 50g bar |\\n| Energy | 1509 kJ | 755 kJ |\\n| Energy | 359 kcal | 180 kcal |\\n| Fat | 11g | 6g |\\n| Carbohydrates | 26g | 13g |\\n| Protein | 32g | 16g |"
                    }
                  ]
                }
                """;

        var result = service.parseMistralResponse(response);

        assertTrue(result.scanSucceeded());
        assertEquals(ScanSource.RAW_TEXT, result.sourceUsed());
        assertEquals(50f, result.parsed().servingSize());
        assertEquals(180f, result.parsed().caloriesPerServing());
        assertEquals(359f, result.parsed().caloriesPer100());
        assertEquals(16f, result.parsed().proteinPerServing());
        assertEquals(32f, result.parsed().proteinPer100());
    }

    @Test
    void parseMistralResponse_prefersRawTableValuesOverAnnotation() throws Exception {
        var response = """
                {
                  "pages": [
                    {
                      "index": 0,
                      "tables": [
                        {
                          "id": "tbl-0.html",
                          "content": "<table><tr><th>TYPICAL VALUES</th><th>Per 100g</th><th>Per serving 40g</th></tr><tr><td>Energy</td><td>1494kJ/354kcal</td><td>598kJ/142kcal</td></tr><tr><td>Fat</td><td>4.1g</td><td>1.7g</td></tr><tr><td>Carbohydrate</td><td>61.7g</td><td>24.7g</td></tr><tr><td>Protein</td><td>12.2g</td><td>4.9g</td></tr></table>"
                        }
                      ]
                    }
                  ],
                  "document_annotation": "{\\"name\\":\\"Wrong Oats\\",\\"brand\\":\\"Wrong Brand\\",\\"store_name\\":\\"Tesco\\",\\"servings_per_container\\":2,\\"total_weight\\":800,\\"total_weight_unit\\":\\"g\\",\\"serving_size\\":50,\\"serving_unit\\":\\"g\\",\\"macros_per_serving\\":{\\"energy_kcal\\":150,\\"fat_g\\":2.0,\\"carbohydrate_g\\":26.0,\\"protein_g\\":6.0},\\"macros_per_100\\":{\\"energy_kcal\\":300,\\"fat_g\\":5.0,\\"carbohydrate_g\\":55.0,\\"protein_g\\":10.0}}"
                }
                """;

        var result = service.parseMistralResponse(response);

        assertTrue(result.scanSucceeded());
        assertEquals(40f, result.parsed().servingSize());
        assertEquals(354f, result.parsed().caloriesPer100());
        assertEquals(142f, result.parsed().caloriesPerServing());
        assertEquals(12.2f, result.parsed().proteinPer100());
        assertEquals(ScanSource.ANNOTATION, result.sourceUsed());
        assertFalse(result.disagreements().isEmpty());
        assertFalse(result.usedAnnotationFallback());
        assertEquals(ScanSource.ANNOTATION, result.productSourceUsed());
    }

    @Test
    void parseMistralResponse_usesAnnotationOnlyToFillMissingRawFields() throws Exception {
        var response = """
                {
                  "pages": [
                    {
                      "index": 0,
                      "tables": [
                        {
                          "id": "tbl-0.html",
                          "content": "<table><tr><th>TYPICAL VALUES</th><th>Per 100g</th></tr><tr><td>Energy</td><td>1494kJ/354kcal</td></tr><tr><td>Fat</td><td>4.1g</td></tr><tr><td>Carbohydrate</td><td>61.7g</td></tr><tr><td>Protein</td><td>12.2g</td></tr></table>"
                        }
                      ]
                    }
                  ],
                  "document_annotation": "{\\"name\\":\\"Oats\\",\\"brand\\":\\"Brand\\",\\"store_name\\":\\"Aldi\\",\\"servings_per_container\\":3,\\"total_weight\\":1200,\\"total_weight_unit\\":\\"g\\",\\"serving_size\\":40,\\"serving_unit\\":\\"g\\",\\"macros_per_serving\\":{\\"energy_kcal\\":142,\\"fat_g\\":1.7,\\"carbohydrate_g\\":24.7,\\"protein_g\\":4.9},\\"macros_per_100\\":{\\"energy_kcal\\":354,\\"fat_g\\":4.1,\\"carbohydrate_g\\":61.7,\\"protein_g\\":12.2}}"
                }
                """;

        var result = service.parseMistralResponse(response);

        assertTrue(result.scanSucceeded());
        assertEquals(ScanSource.ANNOTATION, result.sourceUsed());
        assertFalse(result.usedAnnotationFallback());
        assertEquals(40f, result.parsed().servingSize());
        assertEquals(142f, result.parsed().caloriesPerServing());
        assertEquals(ScanSource.ANNOTATION, result.productSourceUsed());
        assertEquals("Oats", result.product().name());
        assertEquals("Brand", result.product().brand());
        assertEquals(1200f, result.product().totalWeight());
    }

    @Test
    void parseMistralResponse_extractsProductFieldsFromRawMarkdown() throws Exception {
        var response = """
                {
                  "pages": [
                    {
                      "index": 0,
                      "markdown": "# OATS\\nAldi\\nThis pack contains 3 servings\\nNutrition"
                    }
                  ]
                }
                """;

        var result = service.parseMistralResponse(response);

        assertTrue(result.scanSucceeded());
        assertEquals(ScanSource.RAW_TEXT, result.productSourceUsed());
        assertEquals("OATS", result.product().name());
        assertEquals("Aldi", result.product().brand());
        assertEquals("Aldi", result.product().storeName());
        assertEquals(3f, result.product().servingsPerContainer());
        assertNull(result.product().totalWeight());
        assertNull(result.product().drainedWeight());
    }

    @Test
    void parseMistralResponse_stripsMarkdownListMarkersFromProductText() throws Exception {
        var response = """
                {
                  "pages": [
                    {
                      "index": 0,
                      "markdown": "- OATS\\n- Aldi\\n3 servings\\nNutrition"
                    }
                  ]
                }
                """;

        var result = service.parseMistralResponse(response);

        assertTrue(result.scanSucceeded());
        assertEquals(ScanSource.RAW_TEXT, result.productSourceUsed());
        assertEquals("OATS", result.product().name());
        assertEquals("Aldi", result.product().brand());
    }

    @Test
    void parseMistralResponse_stripsMarkdownBlockquoteMarkersFromProductText() throws Exception {
        var response = """
                {
                  "pages": [
                    {
                      "index": 0,
                      "markdown": "> OATS\\n> Aldi\\n> This pack contains 3 servings\\n> Nutrition"
                    }
                  ]
                }
                """;

        var result = service.parseMistralResponse(response);

        assertTrue(result.scanSucceeded());
        assertEquals(ScanSource.RAW_TEXT, result.productSourceUsed());
        assertEquals("OATS", result.product().name());
        assertEquals("Aldi", result.product().brand());
        assertEquals(3f, result.product().servingsPerContainer());
    }

    @Test
    void parseMistralResponse_doesNotUseRearLabelServingSentenceAsProductName() throws Exception {
        var response = """
                {
                  "pages": [
                    {
                      "index": 0,
                      "markdown": "This pack contains approx. 16 slices.\\nNutrition\\nTypical values per 100g: Energy 250kcal\\nStorage: Store in a cool dry place."
                    }
                  ]
                }
                """;

        var result = service.parseMistralResponse(response);

        assertTrue(result.scanSucceeded());
        assertNull(result.product().name());
        assertNull(result.product().servingsPerContainer());
    }

    @Test
    void parseMistralResponse_prefersAnnotationForNameAndBrand() throws Exception {
        var response = """
                {
                  "pages": [
                    {
                      "index": 0,
                      "markdown": "# OATS\\nAldi\\nThis pack contains 3 servings\\n1200g\\nNutrition"
                    }
                  ],
                  "document_annotation": "{\\"name\\":\\"Scottish Oats\\",\\"brand\\":\\"Harvest Morn\\",\\"store_name\\":\\"Aldi\\",\\"servings_per_container\\":4,\\"total_weight\\":1200,\\"total_weight_unit\\":\\"g\\"}"
                }
                """;

        var result = service.parseMistralResponse(response);

        assertTrue(result.scanSucceeded());
        assertEquals("Scottish Oats", result.product().name());
        assertEquals("Harvest Morn", result.product().brand());
        assertEquals("Aldi", result.product().storeName());
        assertEquals(3f, result.product().servingsPerContainer());
        assertEquals(1200f, result.product().totalWeight());
        assertTrue(result.productDisagreements().stream().anyMatch(disagreement -> disagreement.contains("name")));
        assertTrue(result.productDisagreements().stream().anyMatch(disagreement -> disagreement.contains("brand")));
    }

    @Test
    void parseMistralResponse_prefersDrainedWeightForServingsPer100() throws Exception {
        var response = """
                {
                  "pages": [
                    {
                      "index": 0,
                      "markdown": "# SKIPJACK TUNA CHUNKS IN BRINE\\nNUTRITION: Typical values per 100g (drained): Energy 460kJ/109kcal, Fat 1.0g, Carbohydrate 0g, Protein 24.9g. Typical values per 1/2 can (51g, drained): Energy 235kJ/55kcal, Fat 0.5g, Carbohydrate 0g, Protein 12.7g. This can contains 2 servings.\\n145g\\nDrained Weight 102g\\nNot to be sold separately.\\nAldi"
                    }
                  ]
                }
                """;

        var result = service.parseMistralResponse(response);

        assertTrue(result.scanSucceeded());
        assertEquals(ScanSource.RAW_TEXT, result.productSourceUsed());
        assertEquals("SKIPJACK TUNA CHUNKS IN BRINE", result.product().name());
        assertEquals("Aldi", result.product().storeName());
        assertEquals(2f, result.product().servingsPerContainer());
        assertEquals(145f, result.product().totalWeight());
        assertEquals(FoodEntity.Unit.G, result.product().totalWeightUnit());
        assertEquals(102f, result.product().drainedWeight());
        assertEquals(FoodEntity.Unit.G, result.product().drainedWeightUnit());
        assertEquals(1.02f, result.product().getServingsPerContainer100());
    }

    @Test
    void parseMistralResponse_extractsServingSizeFromPerServingTextInsteadOfPer100() throws Exception {
        var response = """
                {
                  "pages": [
                    {
                      "index": 0,
                      "markdown": "# SKIPJACK TUNA CHUNKS IN BRINE\\nNUTRITION: Typical values per 100g (drained): Energy 460kJ/109kcal, Fat 1.0g, Carbohydrate 0g, Protein 24.9g, Salt 0.90g. Typical values per 1/2 can (51g, drained): Energy 235kJ/55kcal, Fat 0.5g, Carbohydrate 0g, Protein 12.7g, Salt 0.46g. This can contains 2 servings.\\n145g\\nDrained Weight 102g"
                    }
                  ],
                  "document_annotation": "{\\"serving_size\\":100,\\"serving_unit\\":\\"g\\",\\"macros_per_serving\\":{\\"energy_kcal\\":55,\\"fat_g\\":0.5,\\"carbohydrate_g\\":0,\\"protein_g\\":12.7},\\"macros_per_100\\":{\\"energy_kcal\\":109,\\"fat_g\\":1.0,\\"carbohydrate_g\\":0,\\"protein_g\\":24.9}}"
                }
                """;

        var result = service.parseMistralResponse(response);

        assertTrue(result.scanSucceeded());
        assertEquals(51f, result.parsed().servingSize());
        assertEquals(FoodEntity.Unit.G, result.parsed().servingUnit());
        assertEquals("per 1/2 can (51g", result.rawNutrients().get("SERVING_SIZE").text());
        assertFalse(result.disagreements().stream().anyMatch(disagreement -> disagreement.contains("servingSize")));
    }

    @Test
    void parseMistralResponse_ignoresContainsNumberWhenNotServings() throws Exception {
        var response = """
                {
                  "pages": [
                    {
                      "index": 0,
                      "markdown": "# Orange Juice\\nContains 12g sugar per 100ml\\nNutrition"
                    }
                  ]
                }
                """;

        var result = service.parseMistralResponse(response);

        assertTrue(result.scanSucceeded());
        assertNull(result.product().servingsPerContainer());
    }

    @Test
    void parseMistralResponse_derivesServingsWhenCountIsMissing() throws Exception {
        var response = """
                {
                  "pages": [
                    {
                      "index": 0,
                      "markdown": "# Orange Juice\\n1L\\nNUTRITION: Typical values per 100ml: Energy 45kcal. Typical values per serving 250ml: Energy 113kcal."
                    }
                  ],
                  "document_annotation": "{\\"serving_size\\":250,\\"serving_unit\\":\\"ml\\",\\"macros_per_serving\\":{\\"energy_kcal\\":113,\\"fat_g\\":0.5,\\"carbohydrate_g\\":26.0,\\"protein_g\\":1.8},\\"macros_per_100\\":{\\"energy_kcal\\":45,\\"fat_g\\":0.2,\\"carbohydrate_g\\":10.4,\\"protein_g\\":0.7}}"
                }
                """;

        var result = service.parseMistralResponse(response);

        assertTrue(result.scanSucceeded());
        assertEquals(FoodEntity.Unit.ML, result.parsed().servingUnit());
        assertEquals(250f, result.parsed().servingSize());
        assertEquals(1000f, result.product().totalWeight());
        assertEquals(FoodEntity.Unit.ML, result.product().totalWeightUnit());
        assertEquals(4f, result.product().servingsPerContainer());
    }

    @Test
    void parseMistralResponse_prefersDerivedServingsOverImplausibleAnnotationFraction() throws Exception {
        var response = """
                {
                  "pages": [
                    {
                      "index": 0,
                      "markdown": "# Whole Milk\\n1L\\nNUTRITION: Typical values per 100ml: Energy 64kcal."
                    }
                  ],
                  "document_annotation": "{\\"serving_size\\":100,\\"serving_unit\\":\\"ml\\",\\"servings_per_container\\":0.5,\\"macros_per_100\\":{\\"energy_kcal\\":64,\\"fat_g\\":3.6,\\"carbohydrate_g\\":4.8,\\"protein_g\\":3.4}}"
                }
                """;

        var result = service.parseMistralResponse(response);

        assertTrue(result.scanSucceeded());
        assertEquals(1000f, result.product().totalWeight());
        assertEquals(FoodEntity.Unit.ML, result.product().totalWeightUnit());
        assertEquals(10f, result.product().servingsPerContainer());
    }

    @Test
    void parseMistralResponse_doesNotDemoteAnnotationWhenRawOnlyHasPer100MlReferences() throws Exception {
        var response = """
                {
                  "pages": [
                    {
                      "index": 0,
                      "markdown": "# Whole Milk\\nNutrition\\nTypical values per 100ml: Energy 64kcal\\nFat 3.6g\\nCarbohydrate 4.8g\\nProtein 3.4g"
                    }
                  ],
                  "document_annotation": "{\\"serving_size\\":200,\\"serving_unit\\":\\"ml\\",\\"servings_per_container\\":5,\\"total_weight\\":1000,\\"total_weight_unit\\":\\"ml\\",\\"macros_per_100\\":{\\"energy_kcal\\":64,\\"fat_g\\":3.6,\\"carbohydrate_g\\":4.8,\\"protein_g\\":3.4}}"
                }
                """;

        var result = service.parseMistralResponse(response);

        assertTrue(result.scanSucceeded());
        assertEquals(200f, result.parsed().servingSize());
        assertEquals(FoodEntity.Unit.ML, result.parsed().servingUnit());
        assertEquals(5f, result.product().servingsPerContainer());
        assertEquals(1000f, result.product().totalWeight());
        assertEquals(FoodEntity.Unit.ML, result.product().totalWeightUnit());
    }

    @Test
    void parseMistralResponse_derivesTrustedTotalWeightFromServingSizeAndCount() throws Exception {
        var response = """
                {
                  "pages": [
                    {
                      "index": 0,
                      "markdown": "# Protein Milk\\nNutrition\\nTypical values per 100ml: Energy 48kcal\\nPer serving 200ml: Energy 95kcal"
                    }
                  ],
                  "document_annotation": "{\\"name\\":\\"Protein Milk\\",\\"brand\\":\\"Clonbawn\\",\\"barcode_number\\":\\"4088600382128\\",\\"serving_size\\":200,\\"serving_unit\\":\\"ml\\",\\"servings_per_container\\":5,\\"total_weight\\":50,\\"total_weight_unit\\":\\"g\\",\\"macros_per_serving\\":{\\"energy_kcal\\":95,\\"fat_g\\":1.7,\\"carbohydrate_g\\":9.7,\\"protein_g\\":10.3},\\"macros_per_100\\":{\\"energy_kcal\\":48,\\"fat_g\\":0.9,\\"carbohydrate_g\\":4.9,\\"protein_g\\":5.1}}"
                }
                """;

        var result = service.parseMistralResponse(response);

        assertTrue(result.scanSucceeded());
        assertEquals(200f, result.parsed().servingSize());
        assertEquals(FoodEntity.Unit.ML, result.parsed().servingUnit());
        assertEquals(5f, result.product().servingsPerContainer());
        assertEquals(1000f, result.product().totalWeight());
        assertEquals(FoodEntity.Unit.ML, result.product().totalWeightUnit());
        assertEquals(10f, result.product().getServingsPerContainer100());
    }

    @Test
    void parseMistralResponse_parsesBeverageAnnotationWithMlUnits() throws Exception {
        var response = """
                {
                  "pages": [
                    {
                      "index": 0,
                      "markdown": "Orange Juice"
                    }
                  ],
                  "document_annotation": "{\\"name\\":\\"Orange Juice\\",\\"serving_size\\":250,\\"serving_unit\\":\\"ml\\",\\"macros_per_serving\\":{\\"energy_kcal\\":113,\\"fat_g\\":0.5,\\"carbohydrate_g\\":26.0,\\"protein_g\\":1.8},\\"macros_per_100\\":{\\"energy_kcal\\":45,\\"fat_g\\":0.2,\\"carbohydrate_g\\":10.4,\\"protein_g\\":0.7}}"
                }
                """;

        var result = service.parseMistralResponse(response);

        assertTrue(result.scanSucceeded());
        assertEquals(ScanSource.ANNOTATION, result.sourceUsed());
        assertEquals(FoodEntity.Unit.ML, result.parsed().servingUnit());
        assertEquals(250f, result.parsed().servingSize());
        assertEquals(113f, result.parsed().caloriesPerServing());
        assertEquals(45f, result.parsed().caloriesPer100());
        assertEquals(10.4f, result.parsed().carbsPer100());
    }

    @Test
    void parseMistralResponse_marksMissingValuesAsFailed() throws Exception {
        var response = """
                {
                  "pages": []
                }
                """;

        var result = service.parseMistralResponse(response);

        assertFalse(result.scanSucceeded());
        assertEquals(1, result.warnings().size());
        assertEquals(ScanSource.NONE, result.sourceUsed());
    }

    @Test
    void mergeScanResults_prefersFrontImageForNameAndBrandWhenMultipleImagesArePresent() {
        var merger = new ScanResultMerger();

        var frontImageResult = new ScanResult(
                true,
                List.of(),
                List.of(),
                List.of(),
                ScanSource.NONE,
                false,
                ScanSource.ANNOTATION,
                false,
                ParsedNutrition.empty(),
                new ProductDetails("Crunchy Peanut Butter", "Harvest Morn", null, "Aldi", null, null, null, null, null),
                Map.of()
        );
        var nutritionImageResult = new ScanResult(
                true,
                List.of(),
                List.of(),
                List.of(),
                ScanSource.RAW_TABLE,
                false,
                ScanSource.ANNOTATION,
                false,
                new ParsedNutrition(15f, FoodEntity.Unit.G, 95f, 634f, 4.3f, 28.5f, 1.8f, 12f, 7.7f, 51.3f),
                new ProductDetails("Peanut Butter", "Aldi", null, "Aldi", 66f, 1000f, FoodEntity.Unit.G, null, null),
                Map.of()
        );

        var merged = merger.mergeScanResults(nutritionImageResult, frontImageResult);

        assertEquals("Crunchy Peanut Butter", merged.product().name());
        assertEquals("Harvest Morn", merged.product().brand());
        assertEquals("Aldi", merged.product().storeName());
        assertEquals(66f, merged.product().servingsPerContainer());
        assertEquals(1000f, merged.product().totalWeight());
        assertEquals(634f, merged.parsed().caloriesPer100());
    }

    @Test
    void mergeScanResults_derivesServingsAcrossImagesUsingWeightAndServingSize() {
        var merger = new ScanResultMerger();

        var frontImageResult = new ScanResult(
                true,
                List.of(),
                List.of(),
                List.of(),
                ScanSource.NONE,
                false,
                ScanSource.RAW_TEXT,
                false,
                ParsedNutrition.empty(),
                new ProductDetails("Orange Juice", "Brand", null, "Tesco", null, 1000f, FoodEntity.Unit.ML, null, null),
                Map.of()
        );
        var nutritionImageResult = new ScanResult(
                true,
                List.of(),
                List.of(),
                List.of(),
                ScanSource.RAW_TABLE,
                false,
                ScanSource.NONE,
                false,
                new ParsedNutrition(250f, FoodEntity.Unit.ML, 113f, 45f, 1.8f, 0.7f, 26f, 10.4f, 0.5f, 0.2f),
                new ProductDetails(null, null, null, null, null, null, null, null, null),
                Map.of()
        );

        var merged = merger.mergeScanResults(nutritionImageResult, frontImageResult);

        assertEquals(4f, merged.product().servingsPerContainer());
        assertEquals(1000f, merged.product().totalWeight());
        assertEquals(FoodEntity.Unit.ML, merged.product().totalWeightUnit());
    }

    @Test
    void mergeScanResults_prefersWeightFromNutritionImageWhenBothImagesHaveWeight() {
        var merger = new ScanResultMerger();

        var nutritionImageResult = new ScanResult(
                true,
                List.of(),
                List.of(),
                List.of(),
                ScanSource.RAW_TABLE,
                false,
                ScanSource.RAW_TEXT,
                false,
                new ParsedNutrition(100f, FoodEntity.Unit.ML, 64f, 64f, 3.4f, 3.4f, 4.8f, 4.8f, 3.6f, 3.6f),
                new ProductDetails(null, null, null, null, null, 1000f, FoodEntity.Unit.ML, null, null),
                Map.of()
        );
        var frontImageResult = new ScanResult(
                true,
                List.of(),
                List.of(),
                List.of(),
                ScanSource.NONE,
                false,
                ScanSource.RAW_TEXT,
                false,
                ParsedNutrition.empty(),
                new ProductDetails("Whole Milk", "Brand", null, "Tesco", null, 1500f, FoodEntity.Unit.ML, null, null),
                Map.of()
        );

        var merged = merger.mergeScanResults(nutritionImageResult, frontImageResult);

        assertEquals(1000f, merged.product().totalWeight());
        assertEquals(FoodEntity.Unit.ML, merged.product().totalWeightUnit());
    }

    @Test
    void mergeScanResults_usesFrontImageOnlyForIdentityWhenFrontNutritionTextIsWrong() {
        var merger = new ScanResultMerger();

        var frontImageResult = new ScanResult(
                true,
                List.of(),
                List.of(),
                List.of(),
                ScanSource.RAW_TEXT,
                false,
                ScanSource.RAW_TEXT,
                false,
                new ParsedNutrition(100f, FoodEntity.Unit.ML, null, 64f, null, 3.4f, null, 4.8f, null, 3.6f),
                new ProductDetails("Whole Milk", "Brand", null, "Tesco", 0.5f, 1000f, FoodEntity.Unit.ML, null, null),
                Map.of()
        );
        var nutritionImageResult = new ScanResult(
                true,
                List.of(),
                List.of(),
                List.of(),
                ScanSource.ANNOTATION,
                false,
                ScanSource.NONE,
                false,
                new ParsedNutrition(200f, FoodEntity.Unit.ML, 128f, 64f, 6.8f, 3.4f, 9.6f, 4.8f, 7.2f, 3.6f),
                new ProductDetails(null, null, null, null, 5f, 1000f, FoodEntity.Unit.ML, null, null),
                Map.of()
        );

        var merged = merger.mergeScanResults(frontImageResult, nutritionImageResult);

        assertEquals("Whole Milk", merged.product().name());
        assertEquals("Brand", merged.product().brand());
        assertEquals(200f, merged.parsed().servingSize());
        assertEquals(FoodEntity.Unit.ML, merged.parsed().servingUnit());
        assertEquals(5f, merged.product().servingsPerContainer());
        assertEquals(1000f, merged.product().totalWeight());
        assertEquals(FoodEntity.Unit.ML, merged.product().totalWeightUnit());
    }

    @Test
    void scan_apiKeyMissing_returnsFailed() {
        var properties = new NutritionScannerProperties();
        properties.setApiKey(null);
        var mockClient = mock(OcrApiClient.class);
        var service = new NutritionScanService(new ObjectMapper(), properties, mockClient);

        var result = service.scan(List.of());

        assertFalse(result.scanSucceeded());
        assertEquals(List.of("Mistral OCR is not configured"), result.warnings());
        verifyNoInteractions(mockClient);
    }

    @Test
    void scan_emptyImages_returnsFailed() {
        var properties = new NutritionScannerProperties();
        properties.setApiKey("test-key");
        var mockClient = mock(OcrApiClient.class);
        var service = new NutritionScanService(new ObjectMapper(), properties, mockClient);

        var result = service.scan(List.of());

        assertFalse(result.scanSucceeded());
        assertEquals(List.of("At least one image is required"), result.warnings());
        verifyNoInteractions(mockClient);
    }

    @Test
    void scan_invalidContentType_returnsFailed() {
        var properties = new NutritionScannerProperties();
        properties.setApiKey("test-key");
        var mockClient = mock(OcrApiClient.class);
        var service = new NutritionScanService(new ObjectMapper(), properties, mockClient);

        var image = new MockMultipartFile("image", "test.txt", "text/plain", new byte[]{1});
        var result = service.scan(List.of(image));

        assertFalse(result.scanSucceeded());
        assertEquals(List.of("Only image uploads are supported"), result.warnings());
        verifyNoInteractions(mockClient);
    }

    @Test
    void scan_success_withAnnotation() throws Exception {
        var properties = new NutritionScannerProperties();
        properties.setApiKey("test-key");
        var mockClient = mock(OcrApiClient.class);
        var service = new NutritionScanService(new ObjectMapper(), properties, mockClient);

        var response = """
                {
                  "pages": [
                    {
                      "index": 0,
                      "markdown": "# Oats"
                    }
                  ],
                  "document_annotation": "{\\"name\\":\\"Oats\\",\\"brand\\":\\"Brand\\",\\"store_name\\":\\"Aldi\\",\\"servings_per_container\\":3,\\"total_weight\\":1200,\\"total_weight_unit\\":\\"g\\",\\"serving_size\\":40,\\"serving_unit\\":\\"g\\",\\"macros_per_serving\\":{\\"energy_kcal\\":142,\\"fat_g\\":1.7,\\"carbohydrate_g\\":24.7,\\"protein_g\\":4.9},\\"macros_per_100\\":{\\"energy_kcal\\":354,\\"fat_g\\":4.1,\\"carbohydrate_g\\":61.7,\\"protein_g\\":12.2}}"
                }
                """;

        when(mockClient.ocrRequest(any())).thenReturn(response);

        var image = new MockMultipartFile("images", "label.png", "image/png", new byte[]{1});
        var result = service.scan(List.of(image));

        assertTrue(result.scanSucceeded());
        assertEquals("Oats", result.product().name());
        assertEquals("Brand", result.product().brand());
        assertEquals("Aldi", result.product().storeName());
        assertEquals(40f, result.parsed().servingSize());
        assertEquals(354f, result.parsed().caloriesPer100());
        assertEquals(142f, result.parsed().caloriesPerServing());
        assertTrue(result.warnings().isEmpty());
        verify(mockClient).ocrRequest(any());
    }

    @Test
    void scan_success_multipleImages_mergesResults() throws Exception {
        var properties = new NutritionScannerProperties();
        properties.setApiKey("test-key");
        var mockClient = mock(OcrApiClient.class);
        var service = new NutritionScanService(new ObjectMapper(), properties, mockClient);

        var response = """
                {
                  "pages": [
                    {
                      "index": 0,
                      "markdown": "# Oats"
                    }
                  ],
                  "document_annotation": "{\\"name\\":\\"Oats\\",\\"brand\\":\\"Brand\\",\\"store_name\\":\\"Aldi\\",\\"servings_per_container\\":3,\\"total_weight\\":1200,\\"total_weight_unit\\":\\"g\\",\\"serving_size\\":40,\\"serving_unit\\":\\"g\\",\\"macros_per_serving\\":{\\"energy_kcal\\":142,\\"fat_g\\":1.7,\\"carbohydrate_g\\":24.7,\\"protein_g\\":4.9},\\"macros_per_100\\":{\\"energy_kcal\\":354,\\"fat_g\\":4.1,\\"carbohydrate_g\\":61.7,\\"protein_g\\":12.2}}"
                }
                """;

        when(mockClient.ocrRequest(any())).thenReturn(response);

        var image1 = new MockMultipartFile("images", "front.png", "image/png", new byte[]{1});
        var image2 = new MockMultipartFile("images", "back.png", "image/png", new byte[]{2});
        var result = service.scan(List.of(image1, image2));

        assertTrue(result.scanSucceeded());
        assertEquals("Oats", result.product().name());
        assertEquals(40f, result.parsed().servingSize());
        assertEquals(354f, result.parsed().caloriesPer100());
        verify(mockClient, times(2)).ocrRequest(any());
    }

    @Test
    void scan_completionExceptionOnIoError_returnsFailed() throws Exception {
        var properties = new NutritionScannerProperties();
        properties.setApiKey("test-key");
        var mockClient = mock(OcrApiClient.class);
        var service = new NutritionScanService(new ObjectMapper(), properties, mockClient);

        when(mockClient.ocrRequest(any())).thenThrow(new IOException("OCR service unavailable"));

        var image = new MockMultipartFile("images", "label.png", "image/png", new byte[]{1});
        var result = service.scan(List.of(image));

        assertFalse(result.scanSucceeded());
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("OCR service unavailable")));
        verify(mockClient).ocrRequest(any());
    }

    @Test
    void scan_multiImage_oneFailsIo_oneSucceeds_returnsSucceededWithWarning() throws Exception {
        var properties = new NutritionScannerProperties();
        properties.setApiKey("test-key");
        var mockClient = mock(OcrApiClient.class);
        var service = new NutritionScanService(new ObjectMapper(), properties, mockClient);

        var successResponse = """
                {
                  "pages": [
                    {
                      "index": 0,
                      "markdown": "# Oats"
                    }
                  ],
                  "document_annotation": "{\\"name\\":\\"Oats\\",\\"brand\\":\\"Brand\\",\\"store_name\\":\\"Aldi\\"}"
                }
                """;

        when(mockClient.ocrRequest(any())).thenAnswer(invocation -> {
            MultipartFile file = invocation.getArgument(0);
            if ("front.png".equals(file.getOriginalFilename())) {
                throw new IOException("OCR service unavailable");
            }
            return successResponse;
        });

        var image1 = new MockMultipartFile("images", "front.png", "image/png", new byte[]{1});
        var image2 = new MockMultipartFile("images", "back.png", "image/png", new byte[]{2});
        var result = service.scan(List.of(image1, image2));

        assertTrue(result.scanSucceeded());
        assertEquals("Oats", result.product().name());
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("OCR service unavailable")));
    }

    @Test
    void scan_multiImage_allFail_returnsFailedWithAllErrors() throws Exception {
        var properties = new NutritionScannerProperties();
        properties.setApiKey("test-key");
        var mockClient = mock(OcrApiClient.class);
        var service = new NutritionScanService(new ObjectMapper(), properties, mockClient);

        when(mockClient.ocrRequest(any())).thenAnswer(invocation -> {
            MultipartFile file = invocation.getArgument(0);
            var name = file.getOriginalFilename();
            if ("front.png".equals(name)) {
                throw new IOException("HTTP 400 bad request");
            }
            throw new IOException("HTTP 503 service unavailable");
        });

        var image1 = new MockMultipartFile("images", "front.png", "image/png", new byte[]{1});
        var image2 = new MockMultipartFile("images", "back.png", "image/png", new byte[]{2});
        var result = service.scan(List.of(image1, image2));

        assertFalse(result.scanSucceeded());
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("Nutrition scan failed")));
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("HTTP 400")));
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("HTTP 503")));
        verify(mockClient, times(2)).ocrRequest(any());
    }

    @Test
    void scan_multiImage_oneSucceeds_twoFailWithDifferentErrors_returnsSucceededWithAllErrors() throws Exception {
        var properties = new NutritionScannerProperties();
        properties.setApiKey("test-key");
        var mockClient = mock(OcrApiClient.class);
        var service = new NutritionScanService(new ObjectMapper(), properties, mockClient);

        var successResponse = """
                {
                  "pages": [
                    {
                      "index": 0,
                      "markdown": "# Oats"
                    }
                  ],
                  "document_annotation": "{\\"name\\":\\"Oats\\",\\"brand\\":\\"Brand\\",\\"store_name\\":\\"Aldi\\"}"
                }
                """;

        when(mockClient.ocrRequest(any())).thenAnswer(invocation -> {
            MultipartFile file = invocation.getArgument(0);
            var name = file.getOriginalFilename();
            if ("front.png".equals(name)) {
                return successResponse;
            } else if ("back.png".equals(name)) {
                throw new IOException("HTTP 400 bad request");
            }
            throw new IOException("Read timed out");
        });

        var image1 = new MockMultipartFile("images", "front.png", "image/png", new byte[]{1});
        var image2 = new MockMultipartFile("images", "back.png", "image/png", new byte[]{2});
        var image3 = new MockMultipartFile("images", "side.png", "image/png", new byte[]{3});
        var result = service.scan(List.of(image1, image2, image3));

        assertTrue(result.scanSucceeded());
        assertEquals("Oats", result.product().name());
        assertEquals("Brand", result.product().brand());
        assertEquals("Aldi", result.product().storeName());
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("HTTP 400")));
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("Read timed out")));
        assertFalse(result.warnings().stream().anyMatch(w -> w.contains("Nutrition scan failed")));
        verify(mockClient, times(3)).ocrRequest(any());
    }

    @Test
    void scan_nullImagesList_returnsFailed() {
        var properties = new NutritionScannerProperties();
        properties.setApiKey("test-key");
        var mockClient = mock(OcrApiClient.class);
        var service = new NutritionScanService(new ObjectMapper(), properties, mockClient);

        var result = service.scan(null);

        assertFalse(result.scanSucceeded());
        assertEquals(List.of("At least one image is required"), result.warnings());
        verifyNoInteractions(mockClient);
    }

    @Test
    void scan_allNullImagesInList_returnsFailed() {
        var properties = new NutritionScannerProperties();
        properties.setApiKey("test-key");
        var mockClient = mock(OcrApiClient.class);
        var service = new NutritionScanService(new ObjectMapper(), properties, mockClient);

        var result = service.scan(Arrays.asList(null, null));

        assertFalse(result.scanSucceeded());
        assertEquals(List.of("At least one image is required"), result.warnings());
        verifyNoInteractions(mockClient);
    }

    @Test
    void scan_mixedContentType_oneGoodOneBad_returnsFailed() {
        var properties = new NutritionScannerProperties();
        properties.setApiKey("test-key");
        var mockClient = mock(OcrApiClient.class);
        var service = new NutritionScanService(new ObjectMapper(), properties, mockClient);

        var validImage = new MockMultipartFile("images", "front.png", "image/png", new byte[]{1});
        var badImage = new MockMultipartFile("images", "notes.txt", "text/plain", new byte[]{2});
        var result = service.scan(List.of(validImage, badImage));

        assertFalse(result.scanSucceeded());
        assertEquals(List.of("Only image uploads are supported"), result.warnings());
        verifyNoInteractions(mockClient);
    }

    @Test
    void scan_multiImage_oneSucceedsTwoFail_logsWarnWithErrorDetails() throws Exception {
        var properties = new NutritionScannerProperties();
        properties.setApiKey("test-key");
        var mockClient = mock(OcrApiClient.class);
        var service = new NutritionScanService(new ObjectMapper(), properties, mockClient);

        var logger = (Logger) LoggerFactory.getLogger(NutritionScanService.class);
        var listAppender = new ListAppender<ILoggingEvent>();
        listAppender.start();
        logger.addAppender(listAppender);

        try {
            var successResponse = """
                    {
                      "pages": [
                        {
                          "index": 0,
                          "markdown": "# Oats"
                        }
                      ],
                      "document_annotation": "{\\"name\\":\\"Oats\\",\\"brand\\":\\"Brand\\",\\"store_name\\":\\"Aldi\\"}"
                    }
                    """;

            when(mockClient.ocrRequest(any())).thenAnswer(invocation -> {
                MultipartFile file = invocation.getArgument(0);
                var name = file.getOriginalFilename();
                if ("front.png".equals(name)) {
                    return successResponse;
                } else if ("back.png".equals(name)) {
                    throw new IOException("HTTP 400 bad request");
                }
                throw new IOException("Read timed out");
            });

            var image1 = new MockMultipartFile("images", "front.png", "image/png", new byte[]{1});
            var image2 = new MockMultipartFile("images", "back.png", "image/png", new byte[]{2});
            var image3 = new MockMultipartFile("images", "side.png", "image/png", new byte[]{3});
            var result = service.scan(List.of(image1, image2, image3));

            assertTrue(result.scanSucceeded());
            assertEquals("Oats", result.product().name());

            var warnEvents = listAppender.list.stream()
                    .filter(e -> e.getLevel().toString().equals("WARN"))
                    .toList();
            assertEquals(2, warnEvents.size());
            assertTrue(warnEvents.get(0).getFormattedMessage().contains("Per-image scan failed error=HTTP 400 bad request"));
            assertTrue(warnEvents.get(1).getFormattedMessage().contains("Per-image scan failed error=Read timed out"));
        } finally {
            logger.detachAppender(listAppender);
        }
    }
}

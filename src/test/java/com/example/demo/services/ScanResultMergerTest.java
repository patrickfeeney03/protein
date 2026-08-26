package com.example.demo.services;

import com.example.demo.entities.FoodEntity;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ScanResultMergerTest {

    private final ScanResultMerger merger = new ScanResultMerger();

    // ============================================================
    // mergePreferAnnotatedNutrition
    // ============================================================

    @Test
    void mergePreferAnnotatedNutrition_bothEmpty_returnsEmpty() {
        var result = merger.mergePreferAnnotatedNutrition(
                ParsedNutrition.empty(), ParsedNutrition.empty(), new ArrayList<>(), new ArrayList<>());
        assertNull(result.servingSize());
        assertNull(result.servingUnit());
        assertNull(result.caloriesPerServing());
        assertNull(result.caloriesPer100());
        assertNull(result.proteinPerServing());
        assertNull(result.proteinPer100());
        assertNull(result.carbsPerServing());
        assertNull(result.carbsPer100());
        assertNull(result.fatPerServing());
        assertNull(result.fatPer100());
    }

    @Test
    void mergePreferAnnotatedNutrition_annotationNull_fallsThroughToRaw() {
        var raw = new ParsedNutrition(100f, FoodEntity.Unit.G, 200f, 250f, 5f, 10f, 20f, 30f, 8f, 12f);
        var result = merger.mergePreferAnnotatedNutrition(
                ParsedNutrition.empty(), raw, new ArrayList<>(), new ArrayList<>());
        assertEquals(100f, result.servingSize());
        assertEquals(FoodEntity.Unit.G, result.servingUnit());
        assertEquals(200f, result.caloriesPerServing());
        assertEquals(250f, result.caloriesPer100());
        assertEquals(5f, result.proteinPerServing());
        assertEquals(10f, result.proteinPer100());
        assertEquals(20f, result.carbsPerServing());
        assertEquals(30f, result.carbsPer100());
        assertEquals(8f, result.fatPerServing());
        assertEquals(12f, result.fatPer100());
    }

    @Test
    void mergePreferAnnotatedNutrition_bothHaveValues_annotationPreferredWhenAgreeing() {
        var annotation = new ParsedNutrition(100f, FoodEntity.Unit.G, 200f, 250f, 5f, 10f, 20f, 30f, 8f, 12f);
        var raw = new ParsedNutrition(100f, FoodEntity.Unit.G, 200f, 250f, 5f, 10f, 20f, 30f, 8f, 12f);
        var result = merger.mergePreferAnnotatedNutrition(
                annotation, raw, new ArrayList<>(), new ArrayList<>());
        assertEquals(200f, result.caloriesPerServing());
    }

    @Test
    void mergePreferAnnotatedNutrition_annotationDisagreesWithRaw_rawPreferred() {
        var annotation = new ParsedNutrition(100f, FoodEntity.Unit.G, 999f, 250f, 5f, 10f, 20f, 30f, 8f, 12f);
        var raw = new ParsedNutrition(100f, FoodEntity.Unit.G, 200f, 250f, 5f, 10f, 20f, 30f, 8f, 12f);
        var disagreements = new ArrayList<String>();
        var result = merger.mergePreferAnnotatedNutrition(
                annotation, raw, new ArrayList<>(), disagreements);
        assertEquals(200f, result.caloriesPerServing());
        assertFalse(disagreements.isEmpty());
        assertTrue(disagreements.get(0).contains("caloriesPerServing"));
    }

    @Test
    void mergePreferAnnotatedNutrition_annotationImplausibleNegative_warnsAndUsesRaw() {
        var annotation = new ParsedNutrition(100f, FoodEntity.Unit.G, -1f, 250f, 5f, 10f, 20f, 30f, 8f, 12f);
        var raw = new ParsedNutrition(100f, FoodEntity.Unit.G, 200f, 250f, 5f, 10f, 20f, 30f, 8f, 12f);
        var warnings = new ArrayList<String>();
        var result = merger.mergePreferAnnotatedNutrition(
                annotation, raw, warnings, new ArrayList<>());
        assertEquals(200f, result.caloriesPerServing());
        assertFalse(warnings.isEmpty());
    }

    @Test
    void mergePreferAnnotatedNutrition_annotationImplausibleAboveUpperBound_warnsAndUsesRaw() {
        var annotation = new ParsedNutrition(100f, FoodEntity.Unit.G, 99999f, 250f, 5f, 10f, 20f, 30f, 8f, 12f);
        var raw = new ParsedNutrition(100f, FoodEntity.Unit.G, 200f, 250f, 5f, 10f, 20f, 30f, 8f, 12f);
        var warnings = new ArrayList<String>();
        var result = merger.mergePreferAnnotatedNutrition(
                annotation, raw, warnings, new ArrayList<>());
        assertEquals(200f, result.caloriesPerServing());
        assertFalse(warnings.isEmpty());
    }

    @Test
    void mergePreferAnnotatedNutrition_servingSizeAndUnitFallback() {
        var annotation = new ParsedNutrition(null, null, 200f, 250f, 5f, 10f, 20f, 30f, 8f, 12f);
        var raw = new ParsedNutrition(100f, FoodEntity.Unit.G, 200f, 250f, 5f, 10f, 20f, 30f, 8f, 12f);
        var result = merger.mergePreferAnnotatedNutrition(
                annotation, raw, new ArrayList<>(), new ArrayList<>());
        assertEquals(100f, result.servingSize());
        assertEquals(FoodEntity.Unit.G, result.servingUnit());
    }

    // ============================================================
    // mergePreferAnnotatedProduct
    // ============================================================

    @Test
    void mergePreferAnnotatedProduct_bothEmpty_returnsEmpty() {
        var result = merger.mergePreferAnnotatedProduct(
                ProductDetails.empty(), ProductDetails.empty(), new ArrayList<>(), new ArrayList<>());
        assertNull(result.name());
        assertNull(result.brand());
        assertNull(result.barcodeNumber());
    }

    @Test
    void mergePreferAnnotatedProduct_annotationEmpty_returnsRaw() {
        var raw = new ProductDetails("Pasta", "Barilla", "12345678", "Tesco", 4f, 500f, FoodEntity.Unit.G, null, null);
        var result = merger.mergePreferAnnotatedProduct(
                ProductDetails.empty(), raw, new ArrayList<>(), new ArrayList<>());
        assertEquals("Pasta", result.name());
        assertEquals("Barilla", result.brand());
    }

    @Test
    void mergePreferAnnotatedProduct_rawEmpty_returnsAnnotation() {
        var annotation = new ProductDetails("Pasta", "Barilla", "12345678", "Tesco", 4f, 500f, FoodEntity.Unit.G, null, null);
        var result = merger.mergePreferAnnotatedProduct(
                annotation, ProductDetails.empty(), new ArrayList<>(), new ArrayList<>());
        assertEquals("Pasta", result.name());
        assertEquals("Barilla", result.brand());
    }

    @Test
    void mergePreferAnnotatedProduct_identityTextMatch_annotationWins() {
        var annotation = new ProductDetails("Pasta", "Barilla", "12345678", null, null, null, null, null, null);
        var raw = new ProductDetails("Pasta", "Barilla", "12345678", "Tesco", 4f, 500f, FoodEntity.Unit.G, null, null);
        var result = merger.mergePreferAnnotatedProduct(
                annotation, raw, new ArrayList<>(), new ArrayList<>());
        assertEquals("Pasta", result.name());
        assertEquals("Barilla", result.brand());
    }

    @Test
    void mergePreferAnnotatedProduct_identityTextMismatch_disagreementRecorded() {
        var annotation = new ProductDetails("Pasta", "Barilla", "12345678", null, null, null, null, null, null);
        var raw = new ProductDetails("Spaghetti", "De Cecco", "87654321", "Tesco", 4f, 500f, FoodEntity.Unit.G, null, null);
        var disagreements = new ArrayList<String>();
        merger.mergePreferAnnotatedProduct(annotation, raw, new ArrayList<>(), disagreements);
        assertFalse(disagreements.isEmpty());
    }

    @Test
    void mergePreferAnnotatedProduct_numericFieldsUseFirstNonNull() {
        var annotation = new ProductDetails("Pasta", "Barilla", "12345678", null, null, null, null, null, null);
        var raw = new ProductDetails(null, null, null, "Tesco", 4f, 500f, FoodEntity.Unit.G, 200f, FoodEntity.Unit.ML);
        var result = merger.mergePreferAnnotatedProduct(
                annotation, raw, new ArrayList<>(), new ArrayList<>());
        assertEquals("Tesco", result.storeName());
        assertEquals(4f, result.servingsPerContainer());
        assertEquals(500f, result.totalWeight());
        assertEquals(FoodEntity.Unit.G, result.totalWeightUnit());
        assertEquals(200f, result.drainedWeight());
        assertEquals(FoodEntity.Unit.ML, result.drainedWeightUnit());
    }

    @Test
    void mergePreferAnnotatedProduct_barcodeImplausible_warnsAndUsesRaw() {
        var annotation = new ProductDetails("Pasta", "Barilla", "12", null, null, null, null, null, null);
        var raw = new ProductDetails("Pasta", "Barilla", "12345678", null, null, null, null, null, null);
        var warnings = new ArrayList<String>();
        var result = merger.mergePreferAnnotatedProduct(
                annotation, raw, warnings, new ArrayList<>());
        assertEquals("12345678", result.barcodeNumber());
        assertFalse(warnings.isEmpty());
    }

    // ============================================================
    // mergeScanResults
    // ============================================================

    @Test
    void mergeScanResults_bothSuccessful_mergedSuccess() {
        var nut1 = new ParsedNutrition(100f, FoodEntity.Unit.G, 200f, 250f, 5f, 10f, 20f, 30f, 8f, 12f);
        var prod1 = new ProductDetails("Pasta", "Barilla", "12345678", "Tesco", 4f, 500f, FoodEntity.Unit.G, null, null);
        var sr1 = new ScanResult(true, List.of("w1"), List.of("d1"), List.of("pd1"),
                ScanSource.RAW_TEXT, false, ScanSource.RAW_TEXT, false,
                nut1, prod1, Map.of("sugar", new RawNutrient("2g")));

        var nut2 = new ParsedNutrition(100f, FoodEntity.Unit.G, 200f, 250f, 5f, 10f, 20f, 30f, 8f, 12f);
        var prod2 = new ProductDetails("Pasta", "Barilla", "12345678", "Tesco", 4f, 500f, FoodEntity.Unit.G, null, null);
        var sr2 = new ScanResult(true, List.of("w2"), List.of("d2"), List.of("pd2"),
                ScanSource.RAW_TABLE, false, ScanSource.RAW_TABLE, false,
                nut2, prod2, Map.of("fiber", new RawNutrient("3g")));

        var result = merger.mergeScanResults(sr1, sr2);
        assertTrue(result.scanSucceeded());
    }

    @Test
    void mergeScanResults_oneFailure_mergedSuccess() {
        var nut1 = new ParsedNutrition(100f, FoodEntity.Unit.G, 200f, 250f, 5f, 10f, 20f, 30f, 8f, 12f);
        var prod1 = new ProductDetails("Pasta", "Barilla", "12345678", "Tesco", 4f, 500f, FoodEntity.Unit.G, null, null);
        var sr1 = new ScanResult(false, List.of(), List.of(), List.of(),
                ScanSource.NONE, false, ScanSource.NONE, false,
                ParsedNutrition.empty(), ProductDetails.empty(), Map.of());
        var sr2 = new ScanResult(true, List.of(), List.of(), List.of(),
                ScanSource.RAW_TEXT, false, ScanSource.RAW_TEXT, false,
                nut1, prod1, Map.of());

        var result = merger.mergeScanResults(sr1, sr2);
        assertTrue(result.scanSucceeded());
    }

    @Test
    void mergeScanResults_bothFailures_mergedFailure() {
        var sr1 = ScanResult.failed(List.of("err1"));
        var sr2 = ScanResult.failed(List.of("err2"));
        var result = merger.mergeScanResults(sr1, sr2);
        assertFalse(result.scanSucceeded());
    }

    @Test
    void mergeScanResults_warningsAndDisagreementsMergedDistinct() {
        var empty = ParsedNutrition.empty();
        var emptyProd = ProductDetails.empty();
        var sr1 = new ScanResult(true, List.of("warn a", "warn b"), List.of("dis 1"), List.of("pd 1"),
                ScanSource.NONE, false, ScanSource.NONE, false, empty, emptyProd, Map.of());
        var sr2 = new ScanResult(true, List.of("warn b", "warn c"), List.of("dis 2"), List.of("pd 2"),
                ScanSource.NONE, false, ScanSource.NONE, false, empty, emptyProd, Map.of());

        var result = merger.mergeScanResults(sr1, sr2);
        assertEquals(3, result.warnings().size());
        assertTrue(result.warnings().containsAll(List.of("warn a", "warn b", "warn c")));
        assertEquals(2, result.disagreements().size());
        assertEquals(2, result.productDisagreements().size());
    }

    @Test
    void mergeScanResults_sourceRanking_prefersHigherRank() {
        var nut = new ParsedNutrition(100f, FoodEntity.Unit.G, 200f, 250f, 5f, 10f, 20f, 30f, 8f, 12f);
        var prod = new ProductDetails("Pasta", "Barilla", "12345678", "Tesco", 4f, 500f, FoodEntity.Unit.G, null, null);
        var sr1 = new ScanResult(true, List.of(), List.of(), List.of(),
                ScanSource.RAW_TEXT, false, ScanSource.RAW_TEXT, false, nut, prod, Map.of());
        var sr2 = new ScanResult(true, List.of(), List.of(), List.of(),
                ScanSource.RAW_TABLE, false, ScanSource.RAW_TABLE, false, nut, prod, Map.of());

        var result = merger.mergeScanResults(sr1, sr2);
        assertEquals(ScanSource.RAW_TABLE, result.sourceUsed());
        assertEquals(ScanSource.RAW_TABLE, result.productSourceUsed());
    }

    @Test
    void mergeScanResults_rawNutrientsMergedWithPutIfAbsent() {
        var empty = ParsedNutrition.empty();
        var emptyProd = ProductDetails.empty();
        var sr1 = new ScanResult(true, List.of(), List.of(), List.of(),
                ScanSource.NONE, false, ScanSource.NONE, false,
                empty, emptyProd, Map.of("a", new RawNutrient("1"), "b", new RawNutrient("2")));
        var sr2 = new ScanResult(true, List.of(), List.of(), List.of(),
                ScanSource.NONE, false, ScanSource.NONE, false,
                empty, emptyProd, Map.of("b", new RawNutrient("override"), "c", new RawNutrient("3")));

        var result = merger.mergeScanResults(sr1, sr2);
        assertEquals(3, result.rawNutrients().size());
        assertEquals("1", result.rawNutrients().get("a").text());
        assertEquals("2", result.rawNutrients().get("b").text());
        assertEquals("3", result.rawNutrients().get("c").text());
    }

    @Test
    void mergeScanResults_annotationFallbackIsOr() {
        var empty = ParsedNutrition.empty();
        var emptyProd = ProductDetails.empty();
        var sr1 = new ScanResult(true, List.of(), List.of(), List.of(),
                ScanSource.NONE, true, ScanSource.NONE, false, empty, emptyProd, Map.of());
        var sr2 = new ScanResult(true, List.of(), List.of(), List.of(),
                ScanSource.NONE, false, ScanSource.NONE, true, empty, emptyProd, Map.of());

        var result = merger.mergeScanResults(sr1, sr2);
        assertTrue(result.usedAnnotationFallback());
        assertTrue(result.productUsedAnnotationFallback());
    }

    // ============================================================
    // resolveServingsPerContainer
    // ============================================================

    @Test
    void resolveServingsPerContainer_nullProduct_returnsEmpty() {
        var nutrition = new ParsedNutrition(100f, FoodEntity.Unit.G, 200f, 250f, 5f, 10f, 20f, 30f, 8f, 12f);
        var result = merger.resolveServingsPerContainer(
                nutrition, null, ProductDetails.empty(), ProductDetails.empty(),
                new ArrayList<>(), new ArrayList<>());
        assertEquals(ProductDetails.empty(), result);
    }

    @Test
    void resolveServingsPerContainer_derivedFromTotalWeight() {
        var nutrition = new ParsedNutrition(100f, FoodEntity.Unit.G, 200f, 250f, 5f, 10f, 20f, 30f, 8f, 12f);
        var merged = new ProductDetails("Pasta", "Barilla", null, null, null, 500f, FoodEntity.Unit.G, null, null);
        var result = merger.resolveServingsPerContainer(
                nutrition, merged, ProductDetails.empty(), ProductDetails.empty(),
                new ArrayList<>(), new ArrayList<>());
        assertEquals(5f, result.servingsPerContainer());
    }

    @Test
    void resolveServingsPerContainer_explicitRawWinsOverDerived() {
        var nutrition = new ParsedNutrition(100f, FoodEntity.Unit.G, 200f, 250f, 5f, 10f, 20f, 30f, 8f, 12f);
        var merged = new ProductDetails("Pasta", "Barilla", null, null, null, 500f, FoodEntity.Unit.G, null, null);
        var raw = new ProductDetails(null, null, null, null, 3f, null, null, null, null);
        var result = merger.resolveServingsPerContainer(
                nutrition, merged, raw, ProductDetails.empty(),
                new ArrayList<>(), new ArrayList<>());
        assertEquals(3f, result.servingsPerContainer());
    }

    @Test
    void resolveServingsPerContainer_explicitRawDisagreesWithAnnotation_warns() {
        var nutrition = new ParsedNutrition(100f, FoodEntity.Unit.G, 200f, 250f, 5f, 10f, 20f, 30f, 8f, 12f);
        var merged = new ProductDetails("Pasta", "Barilla", null, null, null, 500f, FoodEntity.Unit.G, null, null);
        var raw = new ProductDetails(null, null, null, null, 3f, null, null, null, null);
        var annotation = new ProductDetails(null, null, null, null, 8f, null, null, null, null);
        var disagreements = new ArrayList<String>();
        merger.resolveServingsPerContainer(nutrition, merged, raw, annotation, new ArrayList<>(), disagreements);
        assertFalse(disagreements.isEmpty());
    }

    @Test
    void resolveServingsPerContainer_implausibleValue_warnsAndFallsBackToDerived() {
        var nutrition = new ParsedNutrition(100f, FoodEntity.Unit.G, 200f, 250f, 5f, 10f, 20f, 30f, 8f, 12f);
        var merged = new ProductDetails("Pasta", "Barilla", null, null, 999f, 500f, FoodEntity.Unit.G, null, null);
        var warnings = new ArrayList<String>();
        var result = merger.resolveServingsPerContainer(
                nutrition, merged, ProductDetails.empty(), ProductDetails.empty(),
                warnings, new ArrayList<>());
        assertEquals(5f, result.servingsPerContainer());
        assertFalse(warnings.isEmpty());
    }

    @Test
    void resolveServingsPerContainer_unitMismatch_noDerivation() {
        var nutrition = new ParsedNutrition(100f, FoodEntity.Unit.ML, 200f, 250f, 5f, 10f, 20f, 30f, 8f, 12f);
        var merged = new ProductDetails("Pasta", "Barilla", null, null, null, 500f, FoodEntity.Unit.G, null, null);
        var result = merger.resolveServingsPerContainer(
                nutrition, merged, ProductDetails.empty(), ProductDetails.empty(),
                new ArrayList<>(), new ArrayList<>());
        assertNull(result.servingsPerContainer());
    }

    @Test
    void resolveServingsPerContainer_usesDrainedWeightWhenPresent() {
        var nutrition = new ParsedNutrition(100f, FoodEntity.Unit.G, 200f, 250f, 5f, 10f, 20f, 30f, 8f, 12f);
        var merged = new ProductDetails("Pasta", "Barilla", null, null, null, 500f, FoodEntity.Unit.G, 250f, FoodEntity.Unit.G);
        var result = merger.resolveServingsPerContainer(
                nutrition, merged, ProductDetails.empty(), ProductDetails.empty(),
                new ArrayList<>(), new ArrayList<>());
        // 250 / 100 = 2.5
        assertEquals(2.5f, result.servingsPerContainer());
    }

    // ============================================================
    // resolveDerivedProductWeight
    // ============================================================

    @Test
    void resolveDerivedProductWeight_nullProduct_returnsNull() {
        assertNull(merger.resolveDerivedProductWeight(ParsedNutrition.empty(), null));
    }

    @Test
    void resolveDerivedProductWeight_nullNutrition_returnsProduct() {
        var prod = new ProductDetails("Pasta", "Barilla", null, null, null, 500f, FoodEntity.Unit.G, null, null);
        var result = merger.resolveDerivedProductWeight(null, prod);
        assertSame(prod, result);
    }

    @Test
    void resolveDerivedProductWeight_derivesTotalWeightFromServingSize() {
        var nutrition = new ParsedNutrition(100f, FoodEntity.Unit.G, 200f, 250f, 5f, 10f, 20f, 30f, 8f, 12f);
        var prod = new ProductDetails("Pasta", "Barilla", null, null, 4f, null, null, null, null);
        var result = merger.resolveDerivedProductWeight(nutrition, prod);
        assertEquals(400f, result.totalWeight());
        assertEquals(FoodEntity.Unit.G, result.totalWeightUnit());
    }

    @Test
    void resolveDerivedProductWeight_preservesExistingWeight() {
        var nutrition = new ParsedNutrition(100f, FoodEntity.Unit.G, 200f, 250f, 5f, 10f, 20f, 30f, 8f, 12f);
        var prod = new ProductDetails("Pasta", "Barilla", null, null, 4f, 500f, FoodEntity.Unit.G, null, null);
        var result = merger.resolveDerivedProductWeight(nutrition, prod);
        assertEquals(500f, result.totalWeight());
        assertEquals(FoodEntity.Unit.G, result.totalWeightUnit());
    }

    @Test
    void resolveDerivedProductWeight_unitMismatch_clearsThenMayReDerive() {
        var nutrition = new ParsedNutrition(100f, FoodEntity.Unit.G, 200f, 250f, 5f, 10f, 20f, 30f, 8f, 12f);
        var prod = new ProductDetails("Pasta", "Barilla", null, null, 4f, 500f, FoodEntity.Unit.ML, null, null);
        var result = merger.resolveDerivedProductWeight(nutrition, prod);
        // sanitize clears mismatched weight, then re-derives from servingSize * servingsPerContainer
        assertEquals(400f, result.totalWeight());
        assertEquals(FoodEntity.Unit.G, result.totalWeightUnit());
    }

    @Test
    void resolveDerivedProductWeight_missingServingSize_doesNotDerive() {
        var nutrition = new ParsedNutrition(null, FoodEntity.Unit.G, 200f, 250f, 5f, 10f, 20f, 30f, 8f, 12f);
        var prod = new ProductDetails("Pasta", "Barilla", null, null, 4f, null, null, null, null);
        var result = merger.resolveDerivedProductWeight(nutrition, prod);
        assertNull(result.totalWeight());
    }

    @Test
    void resolveDerivedProductWeight_missingServingsPerContainer_doesNotDerive() {
        var nutrition = new ParsedNutrition(100f, FoodEntity.Unit.G, 200f, 250f, 5f, 10f, 20f, 30f, 8f, 12f);
        var prod = new ProductDetails("Pasta", "Barilla", null, null, null, null, null, null, null);
        var result = merger.resolveDerivedProductWeight(nutrition, prod);
        assertNull(result.totalWeight());
    }

    @Test
    void resolveDerivedProductWeight_drainedWeightPreference() {
        var nutrition = new ParsedNutrition(100f, FoodEntity.Unit.G, 200f, 250f, 5f, 10f, 20f, 30f, 8f, 12f);
        var prod = new ProductDetails("Pasta", "Barilla", null, null, null, null, null, 200f, FoodEntity.Unit.G);
        var result = merger.resolveDerivedProductWeight(nutrition, prod);
        // drained weight 200g exists, serving size 100g, but no servingsPerContainer → no derivation
        assertEquals(200f, result.drainedWeight());
        assertEquals(FoodEntity.Unit.G, result.drainedWeightUnit());
    }
}

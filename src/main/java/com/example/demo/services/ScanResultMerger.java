package com.example.demo.services;

import com.example.demo.entities.FoodEntity;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

class ScanResultMerger {
    private static final float MAX_PLAUSIBLE_SERVINGS_PER_CONTAINER = 200f;
    private static final float MIN_PLAUSIBLE_SERVINGS_PER_CONTAINER = 1f;
    private static final float MIN_PLAUSIBLE_SERVING_SIZE = 0.1f;

    ParsedNutrition mergePreferAnnotatedNutrition(
            ParsedNutrition annotationParsed,
            ParsedNutrition rawParsed,
            List<String> warnings,
            List<String> disagreements
    ) {
        return new ParsedNutrition(
                firstNonNull(rawParsed.servingSize(), annotationParsed.servingSize()),
                firstNonNull(rawParsed.servingUnit(), annotationParsed.servingUnit()),
                chooseAnnotatedNutritionValue("caloriesPerServing", annotationParsed.caloriesPerServing(), rawParsed.caloriesPerServing(), 2000f, warnings, disagreements),
                chooseAnnotatedNutritionValue("caloriesPer100", annotationParsed.caloriesPer100(), rawParsed.caloriesPer100(), 1000f, warnings, disagreements),
                chooseAnnotatedNutritionValue("proteinPerServing", annotationParsed.proteinPerServing(), rawParsed.proteinPerServing(), 500f, warnings, disagreements),
                chooseAnnotatedNutritionValue("proteinPer100", annotationParsed.proteinPer100(), rawParsed.proteinPer100(), 100f, warnings, disagreements),
                chooseAnnotatedNutritionValue("carbsPerServing", annotationParsed.carbsPerServing(), rawParsed.carbsPerServing(), 500f, warnings, disagreements),
                chooseAnnotatedNutritionValue("carbsPer100", annotationParsed.carbsPer100(), rawParsed.carbsPer100(), 100f, warnings, disagreements),
                chooseAnnotatedNutritionValue("fatPerServing", annotationParsed.fatPerServing(), rawParsed.fatPerServing(), 500f, warnings, disagreements),
                chooseAnnotatedNutritionValue("fatPer100", annotationParsed.fatPer100(), rawParsed.fatPer100(), 100f, warnings, disagreements)
        );
    }

    ProductDetails mergePreferAnnotatedProduct(
            ProductDetails annotationProduct,
            ProductDetails rawProduct,
            List<String> warnings,
            List<String> disagreements
    ) {
        if (!ScanUtils.hasProductValues(annotationProduct)) {
            return rawProduct;
        }
        if (!ScanUtils.hasProductValues(rawProduct)) {
            return annotationProduct;
        }

        warnOnTextMismatch("name", rawProduct.name(), annotationProduct.name(), disagreements);
        warnOnTextMismatch("brand", rawProduct.brand(), annotationProduct.brand(), disagreements);
        warnOnTextMismatch("barcodeNumber", rawProduct.barcodeNumber(), annotationProduct.barcodeNumber(), disagreements);

        return new ProductDetails(
                chooseAnnotatedIdentityText("name", annotationProduct.name(), rawProduct.name(), warnings, disagreements),
                chooseAnnotatedIdentityText("brand", annotationProduct.brand(), rawProduct.brand(), warnings, disagreements),
                chooseAnnotatedIdentityText("barcodeNumber", annotationProduct.barcodeNumber(), rawProduct.barcodeNumber(), warnings, disagreements),
                firstNonNull(rawProduct.storeName(), annotationProduct.storeName()),
                firstNonNull(rawProduct.servingsPerContainer(), annotationProduct.servingsPerContainer()),
                firstNonNull(rawProduct.totalWeight(), annotationProduct.totalWeight()),
                firstNonNull(rawProduct.totalWeightUnit(), annotationProduct.totalWeightUnit()),
                firstNonNull(rawProduct.drainedWeight(), annotationProduct.drainedWeight()),
                firstNonNull(rawProduct.drainedWeightUnit(), annotationProduct.drainedWeightUnit())
        );
    }

    ScanResult mergeScanResults(ScanResult existing, ScanResult incoming) {
        var warnings = new ArrayList<>(mergeDistinct(existing.warnings(), incoming.warnings()));
        var disagreements = new ArrayList<>(mergeDistinct(existing.disagreements(), incoming.disagreements()));
        var productDisagreements = new ArrayList<>(mergeDistinct(existing.productDisagreements(), incoming.productDisagreements()));

        var parsedNutrition = mergeAcrossImagesNutrition(existing.parsed(), existing.sourceUsed(), incoming.parsed(), incoming.sourceUsed());
        var parsedProduct = mergeAcrossImagesProduct(
                existing.product(),
                existing.productSourceUsed(),
                existing.sourceUsed(),
                incoming.product(),
                incoming.productSourceUsed(),
                incoming.sourceUsed()
        );
        parsedProduct = resolveServingsPerContainer(
                parsedNutrition,
                parsedProduct,
                ProductDetails.empty(),
                ProductDetails.empty(),
                warnings,
                productDisagreements
        );
        parsedProduct = resolveDerivedProductWeight(parsedNutrition, parsedProduct);

        var mergedRawNutrients = new java.util.LinkedHashMap<String, RawNutrient>(existing.rawNutrients());
        incoming.rawNutrients().forEach(mergedRawNutrients::putIfAbsent);

        return new ScanResult(
                existing.scanSucceeded() || incoming.scanSucceeded(),
                List.copyOf(warnings),
                List.copyOf(disagreements),
                List.copyOf(productDisagreements),
                bestSource(existing.sourceUsed(), incoming.sourceUsed()),
                existing.usedAnnotationFallback() || incoming.usedAnnotationFallback(),
                bestSource(existing.productSourceUsed(), incoming.productSourceUsed()),
                existing.productUsedAnnotationFallback() || incoming.productUsedAnnotationFallback(),
                parsedNutrition,
                parsedProduct,
                java.util.Map.copyOf(mergedRawNutrients)
        );
    }

    ProductDetails resolveServingsPerContainer(
            ParsedNutrition parsedNutrition,
            ProductDetails mergedProduct,
            ProductDetails rawProduct,
            ProductDetails annotationProduct,
            List<String> warnings,
            List<String> disagreements
    ) {
        if (mergedProduct == null) {
            return ProductDetails.empty();
        }

        var derived = deriveServingsPerContainer(parsedNutrition, mergedProduct);
        var resolved = resolveServingsPriority(mergedProduct, rawProduct, annotationProduct, derived, disagreements);
        resolved = validatePlausibilityAndFallback(resolved, derived, warnings);

        if (Objects.equals(resolved, mergedProduct.servingsPerContainer())) {
            return mergedProduct;
        }

        return new ProductDetails(
                mergedProduct.name(),
                mergedProduct.brand(),
                mergedProduct.barcodeNumber(),
                mergedProduct.storeName(),
                resolved,
                mergedProduct.totalWeight(),
                mergedProduct.totalWeightUnit(),
                mergedProduct.drainedWeight(),
                mergedProduct.drainedWeightUnit()
        );
    }

    ProductDetails resolveDerivedProductWeight(ParsedNutrition parsedNutrition, ProductDetails product) {
        if (product == null || parsedNutrition == null) {
            return product;
        }
        var trustedWeight = sanitizeTrustedProductWeight(parsedNutrition, product);
        if (trustedWeight.totalWeight() != null || trustedWeight.drainedWeight() != null) {
            return trustedWeight;
        }
        if (parsedNutrition.servingSize() == null || parsedNutrition.servingUnit() == null || trustedWeight.servingsPerContainer() == null) {
            return trustedWeight;
        }

        var derivedTotalWeight = parsedNutrition.servingSize() * trustedWeight.servingsPerContainer();
        if (derivedTotalWeight <= 0f) {
            return trustedWeight;
        }

        return new ProductDetails(
                trustedWeight.name(),
                trustedWeight.brand(),
                trustedWeight.barcodeNumber(),
                trustedWeight.storeName(),
                trustedWeight.servingsPerContainer(),
                derivedTotalWeight,
                parsedNutrition.servingUnit(),
                trustedWeight.drainedWeight(),
                trustedWeight.drainedWeightUnit()
        );
    }

    private Float deriveServingsPerContainer(ParsedNutrition nutrition, ProductDetails product) {
        if (nutrition == null || product == null) {
            return null;
        }

        var servingSize = nutrition.servingSize();
        var servingUnit = nutrition.servingUnit();
        if (servingSize == null || servingUnit == null || servingSize < MIN_PLAUSIBLE_SERVING_SIZE) {
            return null;
        }

        var hasDrained = product.drainedWeight() != null && product.drainedWeight() > 0f;
        var effectiveWeight = hasDrained ? product.drainedWeight() : product.totalWeight();
        var effectiveUnit = hasDrained ? product.drainedWeightUnit() : product.totalWeightUnit();
        if (effectiveWeight == null || effectiveUnit == null || effectiveWeight <= 0f || effectiveUnit != servingUnit) {
            return null;
        }

        var derived = effectiveWeight / servingSize;
        if (derived <= 0f || derived > MAX_PLAUSIBLE_SERVINGS_PER_CONTAINER) {
            return null;
        }

        var nearestInt = Math.round(derived);
        if (nearestInt > 0 && Math.abs(derived - nearestInt) <= 0.12f) {
            return (float) nearestInt;
        }
        return derived;
    }

    private Float resolveServingsPriority(
            ProductDetails mergedProduct,
            ProductDetails rawProduct,
            ProductDetails annotationProduct,
            Float derived,
            List<String> disagreements
    ) {
        var explicitRaw = rawProduct == null ? null : rawProduct.servingsPerContainer();
        var annotation = annotationProduct == null ? null : annotationProduct.servingsPerContainer();
        var existing = mergedProduct.servingsPerContainer();

        if (explicitRaw != null) {
            if (annotation != null && !isSimilar(explicitRaw, annotation)) {
                disagreements.add("Raw OCR servings per container disagrees with Mistral annotation: " + explicitRaw + " vs " + annotation);
            }
            return explicitRaw;
        }
        if (derived != null && existing == null) {
            return derived;
        }
        if (annotation != null) {
            return annotation;
        }
        return existing;
    }

    private Float validatePlausibilityAndFallback(Float resolved, Float derived, List<String> warnings) {
        if (resolved != null && (resolved < MIN_PLAUSIBLE_SERVINGS_PER_CONTAINER || resolved > MAX_PLAUSIBLE_SERVINGS_PER_CONTAINER)) {
            warnings.add("Ignoring implausible servings per container value");
            resolved = null;
        }
        if (resolved == null && derived != null) {
            return derived;
        }
        return resolved;
    }

    private ProductDetails sanitizeTrustedProductWeight(ParsedNutrition parsedNutrition, ProductDetails product) {
        if (product == null || parsedNutrition == null) {
            return product;
        }
        if (product.totalWeight() == null || product.totalWeightUnit() == null || parsedNutrition.servingUnit() == null) {
            return product;
        }
        if (product.totalWeightUnit() == parsedNutrition.servingUnit()) {
            return product;
        }

        return new ProductDetails(
                product.name(),
                product.brand(),
                product.barcodeNumber(),
                product.storeName(),
                product.servingsPerContainer(),
                null,
                null,
                product.drainedWeight(),
                product.drainedWeightUnit()
        );
    }

    private ParsedNutrition mergeAcrossImagesNutrition(
            ParsedNutrition existing,
            ScanSource existingSource,
            ParsedNutrition incoming,
            ScanSource incomingSource
    ) {
        return new ParsedNutrition(
                chooseNutritionValueAcrossImages(existing.servingSize(), existingSource, incoming.servingSize(), incomingSource),
                chooseNutritionUnitAcrossImages(existing.servingUnit(), existingSource, incoming.servingUnit(), incomingSource),
                chooseNutritionValueAcrossImages(existing.caloriesPerServing(), existingSource, incoming.caloriesPerServing(), incomingSource),
                chooseNutritionValueAcrossImages(existing.caloriesPer100(), existingSource, incoming.caloriesPer100(), incomingSource),
                chooseNutritionValueAcrossImages(existing.proteinPerServing(), existingSource, incoming.proteinPerServing(), incomingSource),
                chooseNutritionValueAcrossImages(existing.proteinPer100(), existingSource, incoming.proteinPer100(), incomingSource),
                chooseNutritionValueAcrossImages(existing.carbsPerServing(), existingSource, incoming.carbsPerServing(), incomingSource),
                chooseNutritionValueAcrossImages(existing.carbsPer100(), existingSource, incoming.carbsPer100(), incomingSource),
                chooseNutritionValueAcrossImages(existing.fatPerServing(), existingSource, incoming.fatPerServing(), incomingSource),
                chooseNutritionValueAcrossImages(existing.fatPer100(), existingSource, incoming.fatPer100(), incomingSource)
        );
    }

    private ProductDetails mergeAcrossImagesProduct(
            ProductDetails existing,
            ScanSource existingSource,
            ScanSource existingNutritionSource,
            ProductDetails incoming,
            ScanSource incomingSource,
            ScanSource incomingNutritionSource
    ) {
        return new ProductDetails(
                chooseIdentityText(existing.name(), existingSource, existingNutritionSource, incoming.name(), incomingSource, incomingNutritionSource),
                chooseIdentityText(existing.brand(), existingSource, existingNutritionSource, incoming.brand(), incomingSource, incomingNutritionSource),
                chooseText(existing.barcodeNumber(), existingSource, incoming.barcodeNumber(), incomingSource),
                chooseText(existing.storeName(), existingSource, incoming.storeName(), incomingSource),
                chooseProductMetricAcrossImages(existing.servingsPerContainer(), existingSource, existingNutritionSource, incoming.servingsPerContainer(), incomingSource, incomingNutritionSource),
                chooseProductMetricAcrossImages(existing.totalWeight(), existingSource, existingNutritionSource, incoming.totalWeight(), incomingSource, incomingNutritionSource),
                chooseProductMetricAcrossImages(existing.totalWeightUnit(), existingSource, existingNutritionSource, incoming.totalWeightUnit(), incomingSource, incomingNutritionSource),
                chooseProductMetricAcrossImages(existing.drainedWeight(), existingSource, existingNutritionSource, incoming.drainedWeight(), incomingSource, incomingNutritionSource),
                chooseProductMetricAcrossImages(existing.drainedWeightUnit(), existingSource, existingNutritionSource, incoming.drainedWeightUnit(), incomingSource, incomingNutritionSource)
        );
    }

    private Float chooseNutritionValueAcrossImages(
            Float existingValue,
            ScanSource existingNutritionSource,
            Float incomingValue,
            ScanSource incomingNutritionSource
    ) {
        if (existingValue == null) {
            return incomingValue;
        }
        if (incomingValue == null) {
            return existingValue;
        }
        if (existingNutritionSource == ScanSource.RAW_TEXT && incomingNutritionSource != ScanSource.RAW_TEXT) {
            return incomingValue;
        }
        if (incomingNutritionSource == ScanSource.RAW_TEXT && existingNutritionSource != ScanSource.RAW_TEXT) {
            return existingValue;
        }
        return bestSource(incomingNutritionSource, existingNutritionSource) == incomingNutritionSource ? incomingValue : existingValue;
    }

    private FoodEntity.Unit chooseNutritionUnitAcrossImages(
            FoodEntity.Unit existingValue,
            ScanSource existingNutritionSource,
            FoodEntity.Unit incomingValue,
            ScanSource incomingNutritionSource
    ) {
        if (existingValue == null) {
            return incomingValue;
        }
        if (incomingValue == null) {
            return existingValue;
        }
        if (existingNutritionSource == ScanSource.RAW_TEXT && incomingNutritionSource != ScanSource.RAW_TEXT) {
            return incomingValue;
        }
        if (incomingNutritionSource == ScanSource.RAW_TEXT && existingNutritionSource != ScanSource.RAW_TEXT) {
            return existingValue;
        }
        return bestSource(incomingNutritionSource, existingNutritionSource) == incomingNutritionSource ? incomingValue : existingValue;
    }

    private <T> T chooseProductMetricAcrossImages(
            T existingValue,
            ScanSource existingSource,
            ScanSource existingNutritionSource,
            T incomingValue,
            ScanSource incomingSource,
            ScanSource incomingNutritionSource
    ) {
        if (existingValue == null) {
            return incomingValue;
        }
        if (incomingValue == null) {
            return existingValue;
        }
        if (existingNutritionSource == ScanSource.NONE && incomingNutritionSource != ScanSource.NONE) {
            return incomingValue;
        }
        if (incomingNutritionSource == ScanSource.NONE && existingNutritionSource != ScanSource.NONE) {
            return existingValue;
        }
        return bestSource(incomingSource, existingSource) == incomingSource ? incomingValue : existingValue;
    }

    private List<String> mergeDistinct(List<String> left, List<String> right) {
        var merged = new LinkedHashSet<String>();
        merged.addAll(left);
        merged.addAll(right);
        return List.copyOf(merged);
    }

    private Float chooseValue(Float existingValue, ScanSource existingSource, Float incomingValue, ScanSource incomingSource) {
        if (existingValue == null) {
            return incomingValue;
        }
        if (incomingValue == null) {
            return existingValue;
        }
        return bestSource(incomingSource, existingSource) == incomingSource ? incomingValue : existingValue;
    }

    private String chooseText(String existingValue, ScanSource existingSource, String incomingValue, ScanSource incomingSource) {
        if (existingValue == null || existingValue.isBlank()) {
            return incomingValue;
        }
        if (incomingValue == null || incomingValue.isBlank()) {
            return existingValue;
        }
        return bestSource(incomingSource, existingSource) == incomingSource ? incomingValue : existingValue;
    }

    private String chooseIdentityText(
            String existingValue,
            ScanSource existingSource,
            ScanSource existingNutritionSource,
            String incomingValue,
            ScanSource incomingSource,
            ScanSource incomingNutritionSource
    ) {
        if (existingValue == null || existingValue.isBlank()) {
            return incomingValue;
        }
        if (incomingValue == null || incomingValue.isBlank()) {
            return existingValue;
        }

        boolean existingIsFrontLike = existingNutritionSource == ScanSource.NONE;
        boolean incomingIsFrontLike = incomingNutritionSource == ScanSource.NONE;
        if (existingIsFrontLike != incomingIsFrontLike) {
            return incomingIsFrontLike ? incomingValue : existingValue;
        }

        return chooseText(existingValue, existingSource, incomingValue, incomingSource);
    }

    private Float chooseAnnotatedNutritionValue(
            String field,
            Float annotationValue,
            Float rawValue,
            float saneUpperBound,
            List<String> warnings,
            List<String> disagreements
    ) {
        if (annotationValue == null) {
            return rawValue;
        }
        if (annotationValue < 0f || annotationValue > saneUpperBound) {
            warnings.add("Ignoring implausible annotation value for " + field);
            return rawValue;
        }
        if (rawValue != null && !isSimilar(annotationValue, rawValue)) {
            disagreements.add("Mistral annotation disagrees with raw OCR for " + field + ": " + annotationValue + " vs " + rawValue);
            return rawValue;
        }
        return annotationValue;
    }

    private String chooseAnnotatedIdentityText(
            String field,
            String annotationValue,
            String rawValue,
            List<String> warnings,
            List<String> disagreements
    ) {
        if (annotationValue == null || annotationValue.isBlank()) {
            return rawValue;
        }
        if ("barcodeNumber".equals(field) && !annotationValue.matches("\\d{8,14}")) {
            warnings.add("Ignoring implausible annotation value for " + field);
            return rawValue;
        }
        if (rawValue != null && !rawValue.isBlank() && !ScanUtils.normalizeText(annotationValue).equals(ScanUtils.normalizeText(rawValue))) {
            disagreements.add("Mistral annotation disagrees with raw OCR for " + field + ": " + annotationValue + " vs " + rawValue);
        }
        return annotationValue;
    }

    private boolean isSimilar(Float annotationValue, Float rawValue) {
        var diff = Math.abs(annotationValue - rawValue);
        var tolerance = Math.max(0.5f, Math.abs(rawValue) * 0.05f);
        return diff <= tolerance;
    }

    private FoodEntity.Unit chooseUnit(FoodEntity.Unit existingValue, ScanSource existingSource, FoodEntity.Unit incomingValue, ScanSource incomingSource) {
        if (existingValue == null) {
            return incomingValue;
        }
        if (incomingValue == null) {
            return existingValue;
        }
        return bestSource(incomingSource, existingSource) == incomingSource ? incomingValue : existingValue;
    }

    private ScanSource bestSource(ScanSource left, ScanSource right) {
        return sourceRank(left) >= sourceRank(right) ? left : right;
    }

    private int sourceRank(ScanSource source) {
        return switch (source) {
            case RAW_TABLE -> 4;
            case RAW_TEXT -> 3;
            case ANNOTATION -> 2;
            case NONE -> 1;
        };
    }

    private void warnOnMismatch(String field, Float rawValue, Float annotationValue, List<String> disagreements) {
        if (rawValue == null || annotationValue == null) {
            return;
        }
        if (Math.abs(rawValue - annotationValue) > 0.11f) {
            disagreements.add("Mistral annotation disagrees with raw OCR for " + field + ": " + rawValue + " vs " + annotationValue);
        }
    }

    private void warnOnTextMismatch(String field, String rawValue, String annotationValue, List<String> disagreements) {
        if (rawValue == null || rawValue.isBlank() || annotationValue == null || annotationValue.isBlank()) {
            return;
        }
        if (!ScanUtils.normalizeText(rawValue).equals(ScanUtils.normalizeText(annotationValue))) {
            disagreements.add("Mistral annotation disagrees with raw OCR for " + field + ": " + rawValue + " vs " + annotationValue);
        }
    }

    private <T> T firstNonNull(T current, T candidate) {
        return current != null ? current : candidate;
    }

}

package com.example.demo.services;

import com.example.demo.entities.FoodEntity;

public record ProductDetails(
        String name,
        String brand,
        String barcodeNumber,
        String storeName,
        Float servingsPerContainer,
        Float totalWeight,
        FoodEntity.Unit totalWeightUnit,
        Float drainedWeight,
        FoodEntity.Unit drainedWeightUnit
) {
    public static ProductDetails empty() {
        return new ProductDetails(null, null, null, null, null, null, null, null, null);
    }

    public Float getServingsPerContainer100() {
        var effectiveWeight = drainedWeight != null && drainedWeight > 0f ? drainedWeight : totalWeight;
        if (effectiveWeight == null || effectiveWeight <= 0f) {
            return null;
        }
        return effectiveWeight / 100f;
    }
}

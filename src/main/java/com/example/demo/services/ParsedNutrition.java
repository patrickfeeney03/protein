package com.example.demo.services;

import com.example.demo.entities.FoodEntity;

public record ParsedNutrition(
        Float servingSize,
        FoodEntity.Unit servingUnit,
        Float caloriesPerServing,
        Float caloriesPer100,
        Float proteinPerServing,
        Float proteinPer100,
        Float carbsPerServing,
        Float carbsPer100,
        Float fatPerServing,
        Float fatPer100
) {
    public static ParsedNutrition empty() {
        return new ParsedNutrition(null, null, null, null, null, null, null, null, null, null);
    }
}

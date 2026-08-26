package com.example.demo.DTOs;

import com.example.demo.entities.FoodEntity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class FoodDto {
    private Long id;

    private Long userId;

    private String name;
    private String brand;

    private Float caloriesPerServing;
    private Float proteinPerServing;
    private Float carbsPerServing;
    private Float fatPerServing;

    private Float caloriesPer100;
    private Float proteinPer100;
    private Float carbsPer100;
    private Float fatPer100;

    private Float servingsPerContainer;
    private Float servingSize;
    private FoodEntity.Unit servingUnit;
    private Float totalWeight;

    private Float price;
    private Float proteinPerEuro;

    private String storeName;
    private LocalDate buyDate;

    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();

    private String extraInfo;
    private String productUrl;
    private Instant priceLastVerifiedAt;
    private String priceSource;
    private List<String> imageUrls = new ArrayList<>();
    private Boolean isFavorite;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getBrand() {
        return brand;
    }

    public void setBrand(String brand) {
        this.brand = brand;
    }

    public Float getCaloriesPerServing() {
        return caloriesPerServing;
    }

    public void setCaloriesPerServing(Float caloriesPerServing) {
        this.caloriesPerServing = caloriesPerServing;
    }

    public Float getProteinPerServing() {
        return proteinPerServing;
    }

    public void setProteinPerServing(Float proteinPerServing) {
        this.proteinPerServing = proteinPerServing;
    }

    public Float getCarbsPerServing() {
        return carbsPerServing;
    }

    public void setCarbsPerServing(Float carbsPerServing) {
        this.carbsPerServing = carbsPerServing;
    }

    public Float getFatPerServing() {
        return fatPerServing;
    }

    public void setFatPerServing(Float fatPerServing) {
        this.fatPerServing = fatPerServing;
    }

    public Float getCaloriesPer100() {
        return caloriesPer100;
    }

    public void setCaloriesPer100(Float caloriesPer100) {
        this.caloriesPer100 = caloriesPer100;
    }

    public Float getProteinPer100() {
        return proteinPer100;
    }

    public void setProteinPer100(Float proteinPer100) {
        this.proteinPer100 = proteinPer100;
    }

    public Float getCarbsPer100() {
        return carbsPer100;
    }

    public void setCarbsPer100(Float carbsPer100) {
        this.carbsPer100 = carbsPer100;
    }

    public Float getFatPer100() {
        return fatPer100;
    }

    public void setFatPer100(Float fatPer100) {
        this.fatPer100 = fatPer100;
    }

    public Float getServingsPerContainer() {
        return servingsPerContainer;
    }

    public void setServingsPerContainer(Float servingsPerContainer) {
        this.servingsPerContainer = servingsPerContainer;
    }

    public Float getServingSize() {
        return servingSize;
    }

    public void setServingSize(Float servingSize) {
        this.servingSize = servingSize;
    }

    public FoodEntity.Unit getServingUnit() {
        return servingUnit;
    }

    public void setServingUnit(FoodEntity.Unit servingUnit) {
        this.servingUnit = servingUnit;
    }

    public Float getTotalWeight() {
        return totalWeight;
    }

    public void setTotalWeight(Float totalWeight) {
        this.totalWeight = totalWeight;
    }

    public Float getPrice() {
        return price;
    }

    public void setPrice(Float price) {
        this.price = price;
    }

    public Float getProteinPerEuro() {
        return proteinPerEuro;
    }

    public void setProteinPerEuro(Float proteinPerEuro) {
        this.proteinPerEuro = proteinPerEuro;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getExtraInfo() {
        return extraInfo;
    }

    public void setExtraInfo(String extraInfo) {
        this.extraInfo = extraInfo;
    }

    public String getStoreName() {
        return storeName;
    }

    public void setStoreName(String storeName) {
        this.storeName = storeName;
    }

    public LocalDate getBuyDate() {
        return buyDate;
    }

    public void setBuyDate(LocalDate buyDate) {
        this.buyDate = buyDate;
    }

    public String getProductUrl() {
        return productUrl;
    }

    public void setProductUrl(String productUrl) {
        this.productUrl = productUrl;
    }


    public Instant getPriceLastVerifiedAt() {
        return priceLastVerifiedAt;
    }

    public void setPriceLastVerifiedAt(Instant priceLastVerifiedAt) {
        this.priceLastVerifiedAt = priceLastVerifiedAt;
    }

    public String getPriceSource() {
        return priceSource;
    }

    public void setPriceSource(String priceSource) {
        this.priceSource = priceSource;
    }

    public List<String> getImageUrls() {
        return imageUrls;
    }

    public void setImageUrls(List<String> imageUrls) {
        this.imageUrls = imageUrls == null ? new ArrayList<>() : new ArrayList<>(imageUrls);
    }

    public Boolean getIsFavorite() {
        return isFavorite;
    }

    public void setIsFavorite(Boolean favorite) {
        isFavorite = favorite;
    }
}

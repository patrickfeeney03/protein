package com.example.demo.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
public class FoodEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
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
    private Unit servingUnit;
    private Float totalWeight;

    private Float price;
    private Float proteinPerEuro;

    private String storeName;
    private LocalDate buyDate;

    @Column(length = 2048)
    private String productUrl;
    private String canonicalProductKey;
    private Instant priceLastVerifiedAt;
    private String priceSource;

    @ElementCollection
    @CollectionTable(name = "food_image_urls", joinColumns = @JoinColumn(name = "food_id"))
    @Column(name = "image_url")
    private List<String> imageUrls = new ArrayList<>();

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    private String extraInfo;

    public FoodEntity() {
    }

    public FoodEntity(String name, String brand, Float caloriesPer100, Float proteinPer100, Float carbsPer100, Float fatPer100, Float servingSize, Unit servingUnit, Float totalWeight, Float price, Float servingsPerContainer, Long userId, Float proteinPerEuro, String extraInfo, Float caloriesPerServing, Float proteinPerServing, Float carbsPerServing, Float fatPerServing, String storeName, LocalDate buyDate, String productUrl, String canonicalProductKey, Instant priceLastVerifiedAt, String priceSource, List<String> imageUrls) {
        this.name = name;
        this.brand = brand;

        this.caloriesPerServing = caloriesPerServing;
        this.proteinPerServing = proteinPerServing;
        this.carbsPerServing = carbsPerServing;
        this.fatPerServing = fatPerServing;

        this.caloriesPer100 = caloriesPer100;
        this.proteinPer100 = proteinPer100;
        this.carbsPer100 = carbsPer100;
        this.fatPer100 = fatPer100;

        this.servingSize = servingSize;
        this.servingUnit = servingUnit;
        this.totalWeight = totalWeight;
        this.price = price;
        this.servingsPerContainer = servingsPerContainer;
        this.userId = userId;
        this.proteinPerEuro = proteinPerEuro;
        this.extraInfo = extraInfo;

        this.storeName = storeName;
        this.buyDate = buyDate;
        this.productUrl = productUrl;
        this.canonicalProductKey = canonicalProductKey;
        this.priceLastVerifiedAt = priceLastVerifiedAt;
        this.priceSource = priceSource;
        setImageUrls(imageUrls);
    }

    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }

    public enum Unit {G, ML}

    public record FoodRequest(@NotBlank @Size(max = 200) String name, @NotBlank @Size(max = 200) String brand,
                              @DecimalMin(value = "0.0001") @DecimalMax(value = "1000000") Float servingSize,
                              Unit servingUnit,
                              @DecimalMin(value = "0") @DecimalMax(value = "1000000") Float price,
                              Float servingsPerContainer, Float caloriesPerServing, Float proteinPerServing,
                              Float carbsPerServing, Float fatPerServing,
                              @Size(max = 5000) String extraInfo, @Size(max = 100) String storeName,
                              LocalDate buyDate, @Size(max = 2048) String productUrl, Boolean forceCreate) {
    }

    public record UpdateRequest(Long id, @NotBlank @Size(max = 200) String name,
                                @NotBlank @Size(max = 200) String brand,
                                @DecimalMin(value = "0.0001") @DecimalMax(value = "1000000") Float servingSize,
                                Unit servingUnit,
                                @DecimalMin(value = "0") @DecimalMax(value = "1000000") Float price,
                                Float servingsPerContainer, Float caloriesPerServing, Float proteinPerServing,
                                Float carbsPerServing, Float fatPerServing,
                                @Size(max = 5000) String extraInfo, @Size(max = 100) String storeName,
                                LocalDate buyDate, @Size(max = 2048) String productUrl) {
    }

    public Float getServingsPerContainer() {
        return servingsPerContainer;
    }

    public void setServingsPerContainer(Float servingsPerContainer) {
        this.servingsPerContainer = servingsPerContainer;
    }

    public Float getPrice() {
        return price;
    }

    public void setPrice(Float price) {
        this.price = price;
    }

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

    public Float getServingSize() {
        return servingSize;
    }

    public void setServingSize(Float servingSize) {
        this.servingSize = servingSize;
    }

    public Unit getServingUnit() {
        return servingUnit;
    }

    public void setServingUnit(Unit servingUnit) {
        this.servingUnit = servingUnit;
    }

    public Float getTotalWeight() {
        return totalWeight;
    }

    public void setTotalWeight(Float totalWeight) {
        this.totalWeight = totalWeight;
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

    public Float getProteinPerEuro() {
        return proteinPerEuro;
    }

    public void setProteinPerEuro(Float proteinPerEuro) {
        this.proteinPerEuro = proteinPerEuro;
    }

    public String getExtraInfo() {
        return extraInfo;
    }

    public void setExtraInfo(String extraInfo) {
        this.extraInfo = extraInfo;
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

    public String getCanonicalProductKey() {
        return canonicalProductKey;
    }

    public void setCanonicalProductKey(String canonicalProductKey) {
        this.canonicalProductKey = canonicalProductKey;
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
}

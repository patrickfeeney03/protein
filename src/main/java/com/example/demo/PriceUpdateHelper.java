package com.example.demo;

import com.example.demo.entities.FoodEntity;
import com.example.demo.repositories.FoodRepository;
import org.slf4j.Logger;

import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

public final class PriceUpdateHelper {

    private PriceUpdateHelper() {
    }

    public static boolean applyFetchedData(
            FoodEntity food,
            ProductData productData,
            String priceSource,
            String storeLabel,
            Logger logger,
            FoodRepository foodRepository
    ) {
        return applyFetchedData(food, productData, priceSource, storeLabel, logger, foodRepository, Clock.systemUTC());
    }

    public static boolean applyFetchedData(
            FoodEntity food,
            ProductData productData,
            String priceSource,
            String storeLabel,
            Logger logger,
            FoodRepository foodRepository,
            Clock clock
    ) {
        var url = food.getProductUrl();
        var newPrice = PriceUtils.normalizePrice(productData.price());
        var existingImageUrls = PriceUtils.normalizeImageUrls(food.getImageUrls());
        var fetchedImageUrls = PriceUtils.normalizeImageUrls(productData.imageUrls());
        var priceChanged = PriceUtils.hasPriceChanged(food.getPrice(), newPrice);
        var imagesChanged = !Objects.equals(existingImageUrls, fetchedImageUrls);

        if (!priceChanged && !imagesChanged) {
            return false;
        }

        if (newPrice != null) {
            food.setPrice(newPrice);
            food.setPriceLastVerifiedAt(clock.instant());
            food.setPriceSource(priceSource);
        }
        food.setImageUrls(fetchedImageUrls);

        if (priceChanged
                && food.getProteinPerServing() != null
                && food.getServingsPerContainer() != null
                && newPrice != null
                && newPrice > 0) {
            var totalProtein = food.getProteinPerServing() * food.getServingsPerContainer();
            food.setProteinPerEuro(totalProtein / newPrice);
        }

        foodRepository.save(food);
        logger.info("Updated {} data for foodId={} priceChanged={} imageCount={} url={}",
                storeLabel, food.getId(), priceChanged, fetchedImageUrls.size(), url);
        return true;
    }

    public static int refreshPrices(
            FoodRepository foodRepository,
            Function<String, Optional<? extends ProductData>> fetcher,
            String domain,
            String priceSource,
            String storeLabel,
            Logger logger) {
        return refreshPrices(foodRepository, fetcher, domain, priceSource, storeLabel, logger, Clock.systemUTC());
    }

    public static int refreshPrices(
            FoodRepository foodRepository,
            Function<String, Optional<? extends ProductData>> fetcher,
            String domain,
            String priceSource,
            String storeLabel,
            Logger logger,
            Clock clock) {
        var foods = foodRepository.findByProductUrlIsNotNull();
        int updatedCount = 0;
        int checkedCount = 0;

        for (var food : foods) {
            var url = food.getProductUrl();
            try {
                var retailer = RetailerUrlPolicy.retailerForHost(domain);
                if (url == null || retailer.isEmpty()
                        || !RetailerUrlPolicy.isAllowedRetailerUrl(url, retailer.get())) {
                    continue;
                }
                checkedCount++;
                var productDataOpt = fetcher.apply(url);
                if (productDataOpt.isEmpty()) {
                    logger.debug("{} fetch failed for foodId={} url={}", storeLabel, food.getId(), url);
                    continue;
                }

                if (applyFetchedData(food, productDataOpt.get(), priceSource, storeLabel, logger, foodRepository, clock)) {
                    updatedCount++;
                }
            } catch (Exception e) {
                logger.warn("Failed to update {} data for foodId={} url={}", storeLabel, food.getId(), url, e);
            }
        }
        logger.info("{} price refresh complete. checked={} updated={}", storeLabel, checkedCount, updatedCount);
        return updatedCount;
    }
}

package com.example.demo.services;

import com.example.demo.AldiPriceFetcher;
import com.example.demo.DunnesPriceFetcher;
import com.example.demo.LidlPriceFetcher;
import com.example.demo.PriceUpdateHelper;
import com.example.demo.ProductData;
import com.example.demo.RetailerUrlPolicy;
import com.example.demo.TescoPriceFetcher;
import com.example.demo.entities.FoodEntity;
import com.example.demo.repositories.FoodRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

@Service
public class PriceRefreshService {
    private record DomainEntry(
            RetailerUrlPolicy.Retailer retailer,
            String priceSource,
            String storeLabel,
            Function<String, Optional<? extends ProductData>> fetcher
    ) {
    }

    private final FoodRepository foodRepository;
    private final UserService userService;
    private final List<DomainEntry> domainEntries;
    private final Clock clock;
    private final Logger logger = LoggerFactory.getLogger(PriceRefreshService.class);

    @Autowired
    public PriceRefreshService(
            FoodRepository foodRepository,
            UserService userService,
            AldiPriceFetcher aldiPriceFetcher,
            LidlPriceFetcher lidlPriceFetcher,
            DunnesPriceFetcher dunnesPriceFetcher,
            TescoPriceFetcher tescoPriceFetcher
    ) {
        this.foodRepository = foodRepository;
        this.userService = userService;
        this.clock = Clock.systemUTC();
        this.domainEntries = List.of(
                new DomainEntry(RetailerUrlPolicy.Retailer.ALDI, "ALDI_API", "Aldi", url -> aldiPriceFetcher.fetchProductData(url)),
                new DomainEntry(RetailerUrlPolicy.Retailer.LIDL, "LIDL_HTML", "Lidl", url -> lidlPriceFetcher.fetchProductData(url)),
                new DomainEntry(RetailerUrlPolicy.Retailer.DUNNES, "DUNNES_HTML", "Dunnes", url -> dunnesPriceFetcher.fetchProductData(url)),
                new DomainEntry(RetailerUrlPolicy.Retailer.TESCO, "TESCO_GQL", "Tesco", url -> tescoPriceFetcher.fetchProductData(url))
        );
    }

    PriceRefreshService(
            FoodRepository foodRepository,
            UserService userService,
            AldiPriceFetcher aldiPriceFetcher,
            LidlPriceFetcher lidlPriceFetcher,
            DunnesPriceFetcher dunnesPriceFetcher,
            TescoPriceFetcher tescoPriceFetcher,
            Clock clock
    ) {
        this.foodRepository = foodRepository;
        this.userService = userService;
        this.clock = clock;
        this.domainEntries = List.of(
                new DomainEntry(RetailerUrlPolicy.Retailer.ALDI, "ALDI_API", "Aldi", url -> aldiPriceFetcher.fetchProductData(url)),
                new DomainEntry(RetailerUrlPolicy.Retailer.LIDL, "LIDL_HTML", "Lidl", url -> lidlPriceFetcher.fetchProductData(url)),
                new DomainEntry(RetailerUrlPolicy.Retailer.DUNNES, "DUNNES_HTML", "Dunnes", url -> dunnesPriceFetcher.fetchProductData(url)),
                new DomainEntry(RetailerUrlPolicy.Retailer.TESCO, "TESCO_GQL", "Tesco", url -> tescoPriceFetcher.fetchProductData(url))
        );
    }

    public RefreshAllResult refreshAllFoods(Long requesterId) {
        requireAdmin(requesterId);

        var foods = foodRepository.findByProductUrlIsNotNull();
        int checked = 0;
        int updated = 0;

        for (var food : foods) {
            var result = refreshFoodInternal(food);
            if (result.checked()) {
                checked++;
            }
            if (result.updated()) {
                updated++;
            }
        }

        logger.info("Admin refresh complete. requesterId={} checked={} updated={}", requesterId, checked, updated);
        return new RefreshAllResult(checked, updated);
    }

    public RefreshFoodResult refreshFood(Long foodId, Long requesterId) {
        requireAdmin(requesterId);

        var food = foodRepository.findById(foodId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        var result = refreshFoodInternal(food);
        if (!result.checked()) {
            logger.info("Admin single-food refresh skipped unsupported URL. requesterId={} foodId={} url={}",
                    requesterId, foodId, food.getProductUrl());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Food does not have a supported product URL");
        }

        logger.info("Admin single-food refresh complete. requesterId={} foodId={} store={} updated={}",
                requesterId, foodId, result.store(), result.updated());
        return new RefreshFoodResult(food.getId(), result.store(), result.updated());
    }

    private void requireAdmin(Long requesterId) {
        if (requesterId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        var user = userService.get(requesterId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        if (!user.isAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
    }

    private RefreshResult refreshFoodInternal(FoodEntity food) {
        var url = food.getProductUrl();
        if (url == null || url.isBlank()) {
            return new RefreshResult(false, false, null);
        }

        var entry = domainEntries.stream()
                .filter(e -> RetailerUrlPolicy.isAllowedRetailerUrl(url, e.retailer()))
                .findFirst()
                .orElse(null);
        if (entry == null) {
            return new RefreshResult(false, false, null);
        }

        var productDataOpt = entry.fetcher().apply(url);
        if (productDataOpt.isEmpty()) {
            logger.debug("{} fetch failed for foodId={} url={}", entry.storeLabel(), food.getId(), url);
            return new RefreshResult(true, false, entry.storeLabel().toLowerCase());
        }

        var updated = PriceUpdateHelper.applyFetchedData(
                food, productDataOpt.get(), entry.priceSource(), entry.storeLabel(), logger, foodRepository, clock);
        return new RefreshResult(true, updated, entry.storeLabel().toLowerCase());
    }

    public record RefreshAllResult(int checked, int updated) {
    }

    public record RefreshFoodResult(Long foodId, String store, boolean updated) {
    }

    private record RefreshResult(boolean checked, boolean updated, String store) {
    }
}

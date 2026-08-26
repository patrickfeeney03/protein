package com.example.demo;

import com.example.demo.repositories.FoodRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;

@Service
public class TescoPriceUpdater {
    private static final String TESCO_DOMAIN = "tesco.ie";
    private static final String TESCO_PRICE_SOURCE = "TESCO_GQL";
    private static final String TESCO_STORE_LABEL = "Tesco";

    private final FoodRepository foodRepository;
    private final TescoPriceFetcher priceFetcher;
    private final Logger logger = LoggerFactory.getLogger(TescoPriceUpdater.class);

    public TescoPriceUpdater(FoodRepository foodRepository, TescoPriceFetcher priceFetcher) {
        this.foodRepository = foodRepository;
        this.priceFetcher = priceFetcher;
    }

    @Scheduled(cron = "0 0 9 2-30/2 * *", zone = "Europe/Dublin")
    public int refreshPrices() {
        return refreshPrices(Clock.systemUTC());
    }

    int refreshPrices(Clock clock) {
        return PriceUpdateHelper.refreshPrices(
                foodRepository,
                url -> priceFetcher.fetchProductData(url),
                TESCO_DOMAIN,
                TESCO_PRICE_SOURCE,
                TESCO_STORE_LABEL,
                logger,
                clock
        );
    }
}

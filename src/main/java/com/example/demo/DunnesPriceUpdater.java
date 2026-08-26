package com.example.demo;

import com.example.demo.repositories.FoodRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;

@Service
public class DunnesPriceUpdater {
    private static final String DUNNES_DOMAIN = "dunnesstoresgrocery.com";
    private static final String DUNNES_PRICE_SOURCE = "DUNNES_HTML";
    private static final String DUNNES_STORE_LABEL = "Dunnes";

    private final FoodRepository foodRepository;
    private final DunnesPriceFetcher priceFetcher;
    private final Logger logger = LoggerFactory.getLogger(DunnesPriceUpdater.class);

    public DunnesPriceUpdater(FoodRepository foodRepository, DunnesPriceFetcher priceFetcher) {
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
                DUNNES_DOMAIN,
                DUNNES_PRICE_SOURCE,
                DUNNES_STORE_LABEL,
                logger,
                clock
        );
    }
}

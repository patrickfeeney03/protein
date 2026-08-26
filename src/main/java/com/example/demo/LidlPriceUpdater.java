package com.example.demo;

import com.example.demo.repositories.FoodRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;

@Service
public class LidlPriceUpdater {
    private static final String LIDL_DOMAIN = "lidl.ie";
    private static final String LIDL_PRICE_SOURCE = "LIDL_HTML";
    private static final String LIDL_STORE_LABEL = "Lidl";

    private final FoodRepository foodRepository;
    private final LidlPriceFetcher priceFetcher;
    private final Logger logger = LoggerFactory.getLogger(LidlPriceUpdater.class);

    public LidlPriceUpdater(FoodRepository foodRepository, LidlPriceFetcher priceFetcher) {
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
                LIDL_DOMAIN,
                LIDL_PRICE_SOURCE,
                LIDL_STORE_LABEL,
                logger,
                clock
        );
    }
}

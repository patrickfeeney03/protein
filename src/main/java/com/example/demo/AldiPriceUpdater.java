package com.example.demo;

import com.example.demo.repositories.FoodRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;

@Service
public class AldiPriceUpdater {
    private static final String ALDI_DOMAIN = "aldi.ie";
    private static final String ALDI_PRICE_SOURCE = "ALDI_API";
    private static final String ALDI_STORE_LABEL = "Aldi";

    private final FoodRepository foodRepository;
    private final AldiPriceFetcher priceFetcher;
    private final Logger logger = LoggerFactory.getLogger(AldiPriceUpdater.class);

    public AldiPriceUpdater(FoodRepository foodRepository, AldiPriceFetcher priceFetcher) {
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
                ALDI_DOMAIN,
                ALDI_PRICE_SOURCE,
                ALDI_STORE_LABEL,
                logger,
                clock
        );
    }
}

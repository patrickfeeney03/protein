package com.example.demo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class PriceUtils {

    private PriceUtils() {
    }

    public static List<String> normalizeImageUrls(List<String> imageUrls) {
        if (imageUrls == null || imageUrls.isEmpty()) {
            return List.of();
        }

        var normalized = imageUrls.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(url -> !url.isBlank())
                .distinct()
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        normalized.sort(Comparator.naturalOrder());
        return normalized;
    }

    public static boolean hasPriceChanged(Float existingPrice, Float fetchedPrice) {
        var existingPriceCents = toPriceCents(existingPrice);
        var fetchedPriceCents = toPriceCents(fetchedPrice);
        return fetchedPriceCents != null && !Objects.equals(existingPriceCents, fetchedPriceCents);
    }

    public static Float normalizePrice(Float price) {
        var priceCents = toPriceCents(price);
        return priceCents == null ? null : priceCents / 100f;
    }

    public static Integer toPriceCents(Float price) {
        if (price == null) {
            return null;
        }
        return Math.round(price * 100);
    }
}

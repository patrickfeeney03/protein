package com.example.demo.DTOs;

import java.util.List;

public record AldiCategoryApiResponse(
        Meta meta,
        List<AldiProduct> data
) {
    public record Meta(
            Pagination pagination
            // more
    ) {
    }

    public record Pagination(
            Integer offset,
            Integer limit,
            Integer totalCount
    ) {
    }

    public record AldiProduct(
            String sku,
            String name,
            String brandName,
            String urlSlugText,
            String sellingSize,
            Price price,
            List<Category> categories,
            List<Asset> assets
    ) {
    }

    public record Price(
            Integer amount,
            String currencyCode
    ) {
    }

    public record Category(
            String id,
            String name,
            String urlSlugText
    ) {}

    public record Asset(
            String url,
            Integer maxWidth,
            Integer maxHeight,
            String mimeType
    ) {}



}

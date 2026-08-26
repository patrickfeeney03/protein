package com.example.demo.services;

import com.example.demo.entities.FoodEntity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProductTextParserTest {

    private final ProductTextParser parser = new ProductTextParser();

    // -- parseProductFromMarkdown: edge cases --

    @Test
    void parseProductFromMarkdown_returnsEmptyForNull() {
        assertEquals(ProductDetails.empty(), parser.parseProductFromMarkdown(null));
    }

    @Test
    void parseProductFromMarkdown_returnsEmptyForBlank() {
        assertEquals(ProductDetails.empty(), parser.parseProductFromMarkdown("   "));
    }

    @Test
    void parseProductFromMarkdown_returnsEmptyForEmptyString() {
        assertEquals(ProductDetails.empty(), parser.parseProductFromMarkdown(""));
    }

    // -- parseProductFromMarkdown: product name extraction --

    @Test
    void parseProductFromMarkdown_extractsFirstValidLineAsName() {
        var result = parser.parseProductFromMarkdown("""
                Organic Whole Milk
                500ml
                Some brand
                """);
        assertEquals("Organic Whole Milk", result.name());
    }

    @Test
    void parseProductFromMarkdown_skipsLinesWithWeightPattern() {
        var result = parser.parseProductFromMarkdown("""
                500g
                Actual Product Name
                """);
        assertEquals("Actual Product Name", result.name());
    }

    @Test
    void parseProductFromMarkdown_skipsShortLines() {
        var result = parser.parseProductFromMarkdown("""
                AB
                Full Product Name Here
                """);
        assertEquals("Full Product Name Here", result.name());
    }

    @Test
    void parseProductFromMarkdown_skipsLinesWithServingKeywords() {
        var result = parser.parseProductFromMarkdown("""
                Contains 4 servings
                Real Product
                """);
        assertEquals("Real Product", result.name());
    }

    @Test
    void parseProductFromMarkdown_returnsNullNameWhenAllLinesAreStopWords() {
        var result = parser.parseProductFromMarkdown("""
                Ingredients
                Nutrition Facts
                Best before end of month
                """);
        assertNull(result.name());
    }

    @Test
    void parseProductFromMarkdown_skipsStoreNameLines() {
        var result = parser.parseProductFromMarkdown("""
                Aldi
                Product Name
                """);
        assertEquals("Product Name", result.name());
    }

    @Test
    void parseProductFromMarkdown_cleansMarkdownFromNameLine() {
        var result = parser.parseProductFromMarkdown("""
                # **Organic Milk**
                Some other text
                """);
        assertEquals("Organic Milk", result.name());
    }

    // -- parseProductFromMarkdown: explicit brand extraction --

    @Test
    void parseProductFromMarkdown_extractsExplicitBrand() {
        var result = parser.parseProductFromMarkdown("""
                Product Name
                Brand: Green Valley
                """);
        assertEquals("Green Valley", result.brand());
    }

    @Test
    void parseProductFromMarkdown_extractsExplicitBrandWithDash() {
        var result = parser.parseProductFromMarkdown("""
                Product Name
                Brand - Organic Farms
                """);
        assertEquals("Organic Farms", result.brand());
    }

    @Test
    void parseProductFromMarkdown_implicitBrandIsLineAfterProductName() {
        var result = parser.parseProductFromMarkdown("""
                Whole Milk
                Green Valley Farms
                """);
        assertEquals("Green Valley Farms", result.brand());
    }

    @Test
    void parseProductFromMarkdown_skipsStopWordLineAfterNameForBrand() {
        var result = parser.parseProductFromMarkdown("""
                Whole Milk
                Ingredients
                Brand Name
                """);
        assertEquals("Brand Name", result.brand());
    }

    @Test
    void parseProductFromMarkdown_implicitBrandCanBeNull() {
        var result = parser.parseProductFromMarkdown("Whole Milk");
        assertNull(result.brand());
    }

    @Test
    void parseProductFromMarkdown_explicitBrandTakesPriorityOverImplicit() {
        var result = parser.parseProductFromMarkdown("""
                Product Name
                Brand: Explicit Brand
                Some Other Line
                """);
        assertEquals("Explicit Brand", result.brand());
    }

    // -- parseProductFromMarkdown: store name inference --

    @Test
    void parseProductFromMarkdown_infersAldiStoreName() {
        var result = parser.parseProductFromMarkdown("Aldi\nProduct");
        assertEquals("Aldi", result.storeName());
    }

    @Test
    void parseProductFromMarkdown_infersLidlStoreName() {
        var result = parser.parseProductFromMarkdown("Lidl\nProduct");
        assertEquals("Lidl", result.storeName());
    }

    @Test
    void parseProductFromMarkdown_infersTescoStoreName() {
        var result = parser.parseProductFromMarkdown("Tesco\nProduct");
        assertEquals("Tesco", result.storeName());
    }

    @Test
    void parseProductFromMarkdown_infersDunnesStoreName() {
        var result = parser.parseProductFromMarkdown("Dunnes\nProduct");
        assertEquals("Dunnes", result.storeName());
    }

    @Test
    void parseProductFromMarkdown_returnsNullStoreNameIfNotFound() {
        var result = parser.parseProductFromMarkdown("Product without store");
        assertNull(result.storeName());
    }

    @Test
    void parseProductFromMarkdown_storeNameIsCaseInsensitive() {
        var result = parser.parseProductFromMarkdown("aldi\nProduct");
        assertEquals("Aldi", result.storeName());
    }

    @Test
    void parseProductFromMarkdown_infersDunnesStoresVariant() {
        var result = parser.parseProductFromMarkdown("Dunnes Stores\nProduct");
        assertEquals("Dunnes", result.storeName());
    }

    // -- parseProductFromMarkdown: servings per container --

    @Test
    void parseProductFromMarkdown_parsesExplicitServingsPerContainer() {
        var result = parser.parseProductFromMarkdown("""
                Product
                Servings per container 4
                """);
        assertEquals(4f, result.servingsPerContainer());
    }

    @Test
    void parseProductFromMarkdown_parsesContainsXServings() {
        var result = parser.parseProductFromMarkdown("""
                Product
                Contains 6 servings
                """);
        assertEquals(6f, result.servingsPerContainer());
    }

    @Test
    void parseProductFromMarkdown_parsesThisPackContains() {
        var result = parser.parseProductFromMarkdown("""
                Product
                This pack contains 8 servings
                """);
        assertEquals(8f, result.servingsPerContainer());
    }

    @Test
    void parseProductFromMarkdown_parsesThisBottleContains() {
        var result = parser.parseProductFromMarkdown("""
                Product
                This bottle contains 12 servings
                """);
        assertEquals(12f, result.servingsPerContainer());
    }

    @Test
    void parseProductFromMarkdown_returnsNullServingsWhenNoMatch() {
        var result = parser.parseProductFromMarkdown("Product without servings");
        assertNull(result.servingsPerContainer());
    }

    @Test
    void parseProductFromMarkdown_explicitServingsTakesPriority() {
        var result = parser.parseProductFromMarkdown("""
                Product
                Servings per container 4
                Contains 6 servings
                """);
        assertEquals(4f, result.servingsPerContainer());
    }

    // -- parseProductFromMarkdown: package weight --

    @Test
    void parseProductFromMarkdown_parsesPackageWeightInGrams() {
        var result = parser.parseProductFromMarkdown("""
                Product
                500g
                """);
        assertEquals(500f, result.totalWeight());
        assertEquals(FoodEntity.Unit.G, result.totalWeightUnit());
    }

    @Test
    void parseProductFromMarkdown_parsesPackageWeightInKgNormalizesToGrams() {
        var result = parser.parseProductFromMarkdown("Product\n1kg");
        assertEquals(1000f, result.totalWeight());
        assertEquals(FoodEntity.Unit.G, result.totalWeightUnit());
    }

    @Test
    void parseProductFromMarkdown_parsesPackageWeightInMl() {
        var result = parser.parseProductFromMarkdown("Product\n750ml");
        assertEquals(750f, result.totalWeight());
        assertEquals(FoodEntity.Unit.ML, result.totalWeightUnit());
    }

    @Test
    void parseProductFromMarkdown_parsesPackageWeightInLitersNormalizesToMl() {
        var result = parser.parseProductFromMarkdown("Product\n1l");
        assertEquals(1000f, result.totalWeight());
        assertEquals(FoodEntity.Unit.ML, result.totalWeightUnit());
    }

    @Test
    void parseProductFromMarkdown_picksLargestWeightWhenMultiplePresent() {
        var result = parser.parseProductFromMarkdown("""
                Product
                200g
                500g
                """);
        assertEquals(500f, result.totalWeight());
    }

    @Test
    void parseProductFromMarkdown_skipsWeightInPer100Lines() {
        var result = parser.parseProductFromMarkdown("""
                Product
                Energy per 100g 239 kcal
                Fat 4.1g
                Protein 12.2g
                500g
                """);
        assertEquals(500f, result.totalWeight());
    }

    @Test
    void parseProductFromMarkdown_skipsWeightInDrainedWeightLines() {
        var result = parser.parseProductFromMarkdown("""
                Product
                Drained weight 300g
                500g
                """);
        assertEquals(500f, result.totalWeight());
    }

    @Test
    void parseProductFromMarkdown_skipsWeightInServingLines() {
        var result = parser.parseProductFromMarkdown("""
                Product
                40g serving
                500g
                """);
        assertEquals(500f, result.totalWeight());
    }

    @Test
    void parseProductFromMarkdown_returnsNullWeightWhenNoMatch() {
        var result = parser.parseProductFromMarkdown("Product without weight");
        assertNull(result.totalWeight());
        assertNull(result.totalWeightUnit());
    }

    @Test
    void parseProductFromMarkdown_skipsWeightInStopWordLines() {
        var result = parser.parseProductFromMarkdown("""
                Product
                Ingredients: see below
                500g
                """);
        assertEquals(500f, result.totalWeight());
    }

    // -- parseProductFromMarkdown: drained weight --

    @Test
    void parseProductFromMarkdown_parsesDrainedWeight() {
        var result = parser.parseProductFromMarkdown("""
                Product
                Drained weight 250g
                """);
        assertEquals(250f, result.drainedWeight());
        assertEquals(FoodEntity.Unit.G, result.drainedWeightUnit());
    }

    @Test
    void parseProductFromMarkdown_parsesDrainedWeightShortForm() {
        var result = parser.parseProductFromMarkdown("""
                Product
                Drained wt 300g
                """);
        assertEquals(300f, result.drainedWeight());
    }

    @Test
    void parseProductFromMarkdown_returnsNullDrainedWeightWhenNoMatch() {
        var result = parser.parseProductFromMarkdown("Product without drained weight");
        assertNull(result.drainedWeight());
        assertNull(result.drainedWeightUnit());
    }

    // -- parseProductFromMarkdown: markdown cleanup --

    @Test
    void parseProductFromMarkdown_removesBoldMarkers() {
        var result = parser.parseProductFromMarkdown("**Product Name**");
        assertEquals("Product Name", result.name());
    }

    @Test
    void parseProductFromMarkdown_removesItalicMarkers() {
        var result = parser.parseProductFromMarkdown("_Product Name_");
        assertEquals("Product Name", result.name());
    }

    @Test
    void parseProductFromMarkdown_removesLinks() {
        var result = parser.parseProductFromMarkdown("""
                [see more](http://example.com)
                Product Name
                """);
        assertEquals("Product Name", result.name());
    }

    @Test
    void parseProductFromMarkdown_removesImages() {
        var result = parser.parseProductFromMarkdown("![Product Image](http://example.com/img.jpg)\nProduct Name");
        assertEquals("Product Name", result.name());
    }

    @Test
    void parseProductFromMarkdown_removesHeaderPrefix() {
        var result = parser.parseProductFromMarkdown("## Product Name");
        assertEquals("Product Name", result.name());
    }

    @Test
    void parseProductFromMarkdown_removesBlockquotePrefix() {
        var result = parser.parseProductFromMarkdown("> Product Name");
        assertEquals("Product Name", result.name());
    }

    @Test
    void parseProductFromMarkdown_removesListPrefix() {
        var result = parser.parseProductFromMarkdown("- Product Name");
        assertEquals("Product Name", result.name());
    }

    @Test
    void parseProductFromMarkdown_removesNumberedListPrefix() {
        var result = parser.parseProductFromMarkdown("1. Product Name");
        assertEquals("Product Name", result.name());
    }

    // -- parseProductFromMarkdown: getServingsPerContainer100 convenience --

    @Test
    void getServingsPerContainer100_usesTotalWeight() {
        var result = parser.parseProductFromMarkdown("Product\n500g");
        assertEquals(5f, result.getServingsPerContainer100());
    }

    @Test
    void getServingsPerContainer100_usesDrainedWeightWhenPresent() {
        var result = parser.parseProductFromMarkdown("""
                Product
                800g
                Drained weight 400g
                """);
        assertEquals(4f, result.getServingsPerContainer100());
    }

    @Test
    void getServingsPerContainer100_returnsNullWhenNoWeight() {
        var result = parser.parseProductFromMarkdown("Product without weight");
        assertNull(result.getServingsPerContainer100());
    }

    // -- parseProductFromMarkdown: full integration tests --

    @Test
    void parseProductFromMarkdown_fullExtraction() {
        var result = parser.parseProductFromMarkdown("""
                ## **Organic Whole Milk**
                
                Brand: Green Valley Farms
                
                *Nutrition Facts*
                | Typical Values | Per 100ml | Per 200ml |
                | --- | --- | --- |
                | Energy | 276 kJ / 66 kcal | 552 kJ / 132 kcal |
                | Fat | 3.6g | 7.2g |
                
                Servings per container 4
                1l
                """);
        assertEquals("Organic Whole Milk", result.name());
        assertEquals("Green Valley Farms", result.brand());
        assertNull(result.storeName());
        assertEquals(4f, result.servingsPerContainer());
        assertEquals(1000f, result.totalWeight());
        assertEquals(FoodEntity.Unit.ML, result.totalWeightUnit());
        assertNull(result.drainedWeight());
    }

    @Test
    void parseProductFromMarkdown_fullExtractionWithStoreAndDrainedWeight() {
        var result = parser.parseProductFromMarkdown("""
                Aldi
                
                Corned Beef
                
                Brand: Everyday Meats
                
                Drained weight 200g
                340g
                """);
        assertEquals("Corned Beef", result.name());
        assertEquals("Everyday Meats", result.brand());
        assertEquals("Aldi", result.storeName());
        assertEquals(340f, result.totalWeight());
        assertEquals(FoodEntity.Unit.G, result.totalWeightUnit());
        assertEquals(200f, result.drainedWeight());
        assertEquals(FoodEntity.Unit.G, result.drainedWeightUnit());
        assertNull(result.servingsPerContainer());
        assertEquals(2f, result.getServingsPerContainer100());
    }

    @Test
    void parseProductFromMarkdown_handlesTescoProduct() {
        var result = parser.parseProductFromMarkdown("""
                Tesco
                
                Chicken Breast Fillets
                
                400g
                
                Contains 2 servings
                """);
        assertEquals("Chicken Breast Fillets", result.name());
        assertEquals("Tesco", result.storeName());
        assertEquals(400f, result.totalWeight());
        assertEquals(FoodEntity.Unit.G, result.totalWeightUnit());
        assertEquals(2f, result.servingsPerContainer());
    }

    @Test
    void parseProductFromMarkdown_handlesLidlProduct() {
        var result = parser.parseProductFromMarkdown("""
                Lidl
                
                Italiamo Penne Pasta
                
                500g
                """);
        assertEquals("Italiamo Penne Pasta", result.name());
        assertEquals("Lidl", result.storeName());
        assertEquals(500f, result.totalWeight());
        assertEquals(FoodEntity.Unit.G, result.totalWeightUnit());
    }

    // -- Unicode / special character edge cases --

    @Test
    void parseProductFromMarkdown_accentedNamePreserved() {
        var result = parser.parseProductFromMarkdown("""
                Münster Gouda
                200g
                """);
        assertEquals("Münster Gouda", result.name());
        assertEquals(200f, result.totalWeight());
    }

    @Test
    void parseProductFromMarkdown_nonLatinNameReturnsNull() {
        var result = parser.parseProductFromMarkdown("Суп");
        assertNull(result.name());
    }

    @Test
    void parseProductFromMarkdown_unicodeBulletStripped() {
        var result = parser.parseProductFromMarkdown("""
                ▪ Product Name
                500g
                """);
        assertEquals("Product Name", result.name());
        assertEquals(500f, result.totalWeight());
    }

    @Test
    void parseProductFromMarkdown_whiteBulletStripped() {
        var result = parser.parseProductFromMarkdown("""
                ◦ Product Name
                500g
                """);
        assertEquals("Product Name", result.name());
        assertEquals(500f, result.totalWeight());
    }

    @Test
    void parseProductFromMarkdown_hyphenBulletStripped() {
        var result = parser.parseProductFromMarkdown("""
                ⁃ Product Name
                500g
                """);
        assertEquals("Product Name", result.name());
        assertEquals(500f, result.totalWeight());
    }

    @Test
    void parseProductFromMarkdown_triangularBulletStripped() {
        var result = parser.parseProductFromMarkdown("""
                ‣ Product Name
                500g
                """);
        assertEquals("Product Name", result.name());
        assertEquals(500f, result.totalWeight());
    }

    @Test
    void parseProductFromMarkdown_curlyQuotesInNamePreserved() {
        var result = parser.parseProductFromMarkdown("""
                “Organic Milk”
                1l
                """);
        assertEquals("“Organic Milk”", result.name());
        assertEquals(1000f, result.totalWeight());
        assertEquals(FoodEntity.Unit.ML, result.totalWeightUnit());
    }

    @Test
    void parseProductFromMarkdown_nonBreakingSpaceInWeightNotMatched() {
        var result = parser.parseProductFromMarkdown("Product\n500\u00A0g");
        assertEquals("Product", result.name());
        assertNull(result.totalWeight());
    }

    @Test
    void parseProductFromMarkdown_emDashInServingsStillMatched() {
        var result = parser.parseProductFromMarkdown("""
                Product
                Servings per container—4
                500g
                """);
        assertEquals(4f, result.servingsPerContainer());
    }

    @Test
    void parseProductFromMarkdown_trademarkSymbolInName() {
        var result = parser.parseProductFromMarkdown("""
                Cheddar®
                200g
                """);
        assertEquals("Cheddar®", result.name());
        assertEquals(200f, result.totalWeight());
    }
}

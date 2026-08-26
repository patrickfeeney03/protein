package com.example.demo.dedup;

import com.example.demo.entities.FoodEntity;
import com.example.demo.repositories.FoodRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FoodDedupServiceTest {

    @Mock
    private FoodRepository foodRepository;

    @InjectMocks
    private FoodDedupService foodDedupService;

    @Test
    void computeCanonicalProductKey_extractsTescoTpnc() {
        var key = foodDedupService.computeCanonicalProductKey(
                "https://www.tesco.ie/groceries/en-IE/products/123456789?preserved=true#frag"
        );

        assertTrue(key.isPresent());
        assertEquals("tesco:123456789", key.get());
    }

    @Test
    void computeCanonicalProductKey_extractsTescoTpncForSchemeLessUrl() {
        var key = foodDedupService.computeCanonicalProductKey(
                "www.tesco.ie/groceries/en-IE/products/123456789"
        );

        assertTrue(key.isPresent());
        assertEquals("tesco:123456789", key.get());
    }

    @Test
    void computeCanonicalProductKey_doesNotTreatLookalikeDomainAsRetailer() {
        var key = foodDedupService.computeCanonicalProductKey(
                "https://nottesco.ie.example.com/groceries/en-IE/products/123456789"
        );

        assertTrue(key.isPresent());
        assertTrue(key.get().startsWith("urlhash:"));
    }

    @Test
    void findLikelyDuplicates_returnsHighScoreForNearIdenticalFood() {
        var existing = new FoodEntity();
        existing.setId(42L);
        existing.setName("Chicken Breast Fillets");
        existing.setBrand("Tesco");
        existing.setCaloriesPer100(120f);
        existing.setProteinPer100(24f);
        existing.setCarbsPer100(0f);
        existing.setFatPer100(2f);
        existing.setTotalWeight(800f);
        existing.setPrice(8.99f);

        when(foodRepository.findTop100ByNameContainingIgnoreCaseOrBrandContainingIgnoreCase("chicken breast", "tesco"))
                .thenReturn(List.of(existing));

        var request = new FoodEntity.FoodRequest(
                "Chicken Breast",
                "Tesco",
                200f,
                FoodEntity.Unit.G,
                8.99f,
                4f,
                240f,
                48f,
                0f,
                4f,
                null,
                null,
                null,
                null,
                false
        );

        var candidates = foodDedupService.findLikelyDuplicates(
                request,
                120f,
                24f,
                0f,
                2f,
                800f,
                8.99f
        );

        assertFalse(candidates.isEmpty());
        assertEquals(42L, candidates.getFirst().foodId());
        assertTrue(candidates.getFirst().score() >= 0.88d);
        assertTrue(foodDedupService.isPossibleDuplicate(candidates));
    }

    @Test
    void findLikelyDuplicates_flagsSameNameWhenBrandIsTypo() {
        var existing = new FoodEntity();
        existing.setId(11L);
        existing.setName("Chicken Breast");
        existing.setBrand("Tesco");
        existing.setCaloriesPer100(120f);
        existing.setProteinPer100(24f);
        existing.setCarbsPer100(0f);
        existing.setFatPer100(2f);
        existing.setTotalWeight(750f);

        when(foodRepository.findTop100ByNameContainingIgnoreCaseOrBrandContainingIgnoreCase("chicken breast", "tescoo"))
                .thenReturn(List.of(existing));

        var request = new FoodEntity.FoodRequest(
                "Chicken Breast",
                "Tescoo",
                250f,
                FoodEntity.Unit.G,
                null,
                3f,
                300f,
                60f,
                0f,
                5f,
                null,
                null,
                null,
                null,
                false
        );

        var candidates = foodDedupService.findLikelyDuplicates(
                request,
                120f,
                24f,
                0f,
                2f,
                750f,
                null
        );

        assertFalse(candidates.isEmpty());
        assertTrue(candidates.getFirst().matchReasons().contains("same_name"));
        assertTrue(candidates.getFirst().matchReasons().contains("brand_typo_match"));
        assertTrue(foodDedupService.isPossibleDuplicate(candidates));
    }

    @Test
    void findLikelyDuplicates_flagsSameNameWithDifferentBrandWhenMacrosAreClose() {
        var existing = new FoodEntity();
        existing.setId(22L);
        existing.setName("Chicken Breast");
        existing.setBrand("Store A");
        existing.setCaloriesPer100(120f);
        existing.setProteinPer100(24f);
        existing.setCarbsPer100(0f);
        existing.setFatPer100(2f);
        existing.setTotalWeight(300f);

        when(foodRepository.findTop100ByNameContainingIgnoreCaseOrBrandContainingIgnoreCase("chicken breast", "store b"))
                .thenReturn(List.of(existing));

        var request = new FoodEntity.FoodRequest(
                "Chicken Breast",
                "Store B",
                100f,
                FoodEntity.Unit.G,
                null,
                3f,
                360f,
                75f,
                1f,
                9f,
                null,
                null,
                null,
                null,
                false
        );

        var candidates = foodDedupService.findLikelyDuplicates(
                request,
                120f,
                25f,
                0f,
                3f,
                300f,
                null
        );

        assertFalse(candidates.isEmpty());
        assertTrue(candidates.getFirst().matchReasons().contains("same_name"));
        assertTrue(candidates.getFirst().matchReasons().contains("macros_close"));
        assertTrue(foodDedupService.isPossibleDuplicate(candidates));
    }

    @Test
    void findLikelyDuplicates_returnsEmptyForBlankNameAndBrandWithoutRepositoryScan() {
        var request = new FoodEntity.FoodRequest(
                "   ",
                "   ",
                100f,
                FoodEntity.Unit.G,
                null,
                1f,
                100f,
                10f,
                10f,
                10f,
                null,
                null,
                null,
                null,
                false
        );

        var candidates = foodDedupService.findLikelyDuplicates(
                request,
                100f,
                10f,
                10f,
                10f,
                100f,
                null
        );

        assertTrue(candidates.isEmpty());
        verifyNoInteractions(foodRepository);
    }

    @Test
    void findLikelyDuplicates_matchesSuffixNameVariantWhenNumericSignalsAreClose() {
        var existing = new FoodEntity();
        existing.setId(77L);
        existing.setName("Max 200g");
        existing.setBrand("Brand");
        existing.setCaloriesPer100(180f);
        existing.setProteinPer100(20f);
        existing.setCarbsPer100(10f);
        existing.setFatPer100(7f);
        existing.setTotalWeight(200f);
        existing.setPrice(2.99f);

        when(foodRepository.findTop100ByNameContainingIgnoreCaseOrBrandContainingIgnoreCase("max 200g fresh", "brand"))
                .thenReturn(List.of());
        when(foodRepository.findTop100ByNameContainingIgnoreCase("fresh")).thenReturn(List.of());
        when(foodRepository.findTop100ByNameContainingIgnoreCaseOrBrandContainingIgnoreCase("fresh", "brand"))
                .thenReturn(List.of());
        when(foodRepository.findTop100ByNameContainingIgnoreCase("200g")).thenReturn(List.of(existing));
        when(foodRepository.findTop100ByNameContainingIgnoreCaseOrBrandContainingIgnoreCase("200g", "brand"))
                .thenReturn(List.of(existing));
        when(foodRepository.findTop100ByNameContainingIgnoreCase("max")).thenReturn(List.of(existing));
        when(foodRepository.findTop100ByNameContainingIgnoreCaseOrBrandContainingIgnoreCase("max", "brand"))
                .thenReturn(List.of(existing));

        var request = new FoodEntity.FoodRequest(
                "Max 200g fresh",
                "Brand",
                200f,
                FoodEntity.Unit.G,
                2.99f,
                1f,
                360f,
                40f,
                20f,
                14f,
                null,
                null,
                null,
                null,
                false
        );

        var candidates = foodDedupService.findLikelyDuplicates(
                request,
                180f,
                20f,
                10f,
                7f,
                200f,
                2.99f
        );

        assertFalse(candidates.isEmpty());
        assertEquals(77L, candidates.getFirst().foodId());
        assertTrue(candidates.getFirst().score() >= 0.88d);
        assertTrue(candidates.getFirst().matchReasons().contains("numeric_high_confidence"));
        assertTrue(foodDedupService.isPossibleDuplicate(candidates));
    }

    @Test
    void computeCanonicalProductKey_returnsEmptyForNullBlankOrBadUrl() {
        assertTrue(foodDedupService.computeCanonicalProductKey(null).isEmpty());
        assertTrue(foodDedupService.computeCanonicalProductKey("   ").isEmpty());
        assertTrue(foodDedupService.computeCanonicalProductKey(":::not-a-url").isEmpty());
    }

    @Test
    void computeCanonicalProductKey_extractsLidlProductId() {
        var key = foodDedupService.computeCanonicalProductKey("https://www.lidl.ie/p54321");

        assertTrue(key.isPresent());
        assertEquals("lidl:54321", key.get());
    }

    @Test
    void computeCanonicalProductKey_extractsDunnesStoreAndProduct() {
        var key = foodDedupService.computeCanonicalProductKey("https://www.dunnesstoresgrocery.com/rsid/9876/departments/food/id-12345");

        assertTrue(key.isPresent());
        assertEquals("dunnes:9876:12345", key.get());
    }

    @Test
    void computeCanonicalProductKey_returnsStableHashForUnknownHost() {
        var key = foodDedupService.computeCanonicalProductKey("www.Example.com/some/path/");

        assertTrue(key.isPresent());
        assertTrue(key.get().startsWith("urlhash:"));
        assertEquals(8 + 16, key.get().length());
    }

    @Test
    void findExactDuplicate_returnsEmptyForBlankKey() {
        assertTrue(foodDedupService.findExactDuplicate("   ").isEmpty());
        verifyNoInteractions(foodRepository);
    }

    @Test
    void findLikelyDuplicates_dropsCandidatesBelowScoreFloor() {
        var unrelated = food(5L, "Totally Different", "OtherBrand", 10f, 1f, 1f, 1f, 10f, 1f);
        when(foodRepository.findTop100ByNameContainingIgnoreCaseOrBrandContainingIgnoreCase("apple", "brandx"))
                .thenReturn(List.of(unrelated));

        var request = new FoodEntity.FoodRequest(
                "Apple",
                "BrandX",
                50f,
                FoodEntity.Unit.G,
                9.99f,
                1f,
                100f,
                1f,
                1f,
                1f,
                null,
                null,
                null,
                null,
                false
        );

        var candidates = foodDedupService.findLikelyDuplicates(
                request,
                200f,
                10f,
                10f,
                10f,
                500f,
                19.99f
        );

        assertTrue(candidates.isEmpty());
    }

    @Test
    void findLikelyDuplicates_limitsToFiveHighestScoringCandidates() {
        var candidates = List.of(
                food(1L, "Item", "Brand", 100f, 10f, 5f, 1f, 100f, 1.00f),
                food(2L, "Item", "Brand", 100f, 10f, 5f, 1f, 100f, 1.10f),
                food(3L, "Item", "Brand", 100f, 10f, 5f, 1f, 100f, 1.20f),
                food(4L, "Item", "Brand", 100f, 10f, 5f, 1f, 100f, 1.30f),
                food(5L, "Item", "Brand", 100f, 10f, 5f, 1f, 100f, 1.40f),
                food(6L, "Item", "Brand", 50f, 5f, 2f, 1f, 50f, 5.00f) // clearly worse
        );
        when(foodRepository.findTop100ByNameContainingIgnoreCaseOrBrandContainingIgnoreCase("item", "brand"))
                .thenReturn(candidates);

        var request = new FoodEntity.FoodRequest(
                "Item",
                "Brand",
                100f,
                FoodEntity.Unit.G,
                1.00f,
                1f,
                200f,
                20f,
                10f,
                2f,
                null,
                null,
                null,
                null,
                false
        );

        var results = foodDedupService.findLikelyDuplicates(
                request,
                100f,
                10f,
                5f,
                1f,
                100f,
                1.00f
        );

        assertEquals(5, results.size());
        assertEquals(1L, results.getFirst().foodId());
        assertTrue(results.stream().noneMatch(c -> c.foodId() == 6L));
    }

    @Test
    void isPossibleDuplicate_respectsThreshold() {
        var below = new DedupCandidate(1L, "Name", "Brand", 0.87d, List.of());
        var atThreshold = new DedupCandidate(2L, "Name", "Brand", 0.88d, List.of());

        assertFalse(foodDedupService.isPossibleDuplicate(List.of(below)));
        assertTrue(foodDedupService.isPossibleDuplicate(List.of(atThreshold)));
    }

    private FoodEntity food(
            Long id,
            String name,
            String brand,
            Float calories,
            Float protein,
            Float carbs,
            Float fat,
            Float weight,
            Float price
    ) {
        var f = new FoodEntity();
        f.setId(id);
        f.setName(name);
        f.setBrand(brand);
        f.setCaloriesPer100(calories);
        f.setProteinPer100(protein);
        f.setCarbsPer100(carbs);
        f.setFatPer100(fat);
        f.setTotalWeight(weight);
        f.setPrice(price);
        return f;
    }
}

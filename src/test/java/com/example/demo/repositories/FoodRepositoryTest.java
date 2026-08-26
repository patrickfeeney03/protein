package com.example.demo.repositories;

import com.example.demo.entities.FoodEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect")
class FoodRepositoryTest {

    @Autowired
    private FoodRepository repository;

    private FoodEntity createFood(String name, String brand, String productUrl, String canonicalProductKey) {
        var entity = new FoodEntity();
        entity.setName(name);
        entity.setBrand(brand);
        entity.setProductUrl(productUrl);
        entity.setCanonicalProductKey(canonicalProductKey);
        return repository.save(entity);
    }

    @Test
    void findByProductUrlIsNotNull_returnsFoodsWithProductUrl() {
        createFood("Oats", "BrandA", "http://example.com/oats", "key1");
        createFood("Rice", "BrandB", null, "key2");
        createFood("Beans", "BrandA", "http://example.com/beans", "key3");

        List<FoodEntity> result = repository.findByProductUrlIsNotNull();

        assertThat(result).hasSize(2);
        assertThat(result).extracting(FoodEntity::getName)
                .containsExactlyInAnyOrder("Oats", "Beans");
    }

    @Test
    void findByProductUrlIsNotNull_returnsEmpty_whenNoneHaveUrl() {
        createFood("Oats", "BrandA", null, "key1");

        List<FoodEntity> result = repository.findByProductUrlIsNotNull();

        assertThat(result).isEmpty();
    }

    @Test
    void findById_loadsImageUrlsEagerly() {
        var food = createFood("Oats", "BrandA", null, "key1");
        food.setImageUrls(List.of("http://example.com/img1.jpg"));
        repository.save(food);

        FoodEntity loaded = repository.findById(food.getId()).orElseThrow();

        assertThat(loaded.getImageUrls()).containsExactly("http://example.com/img1.jpg");
    }

    @Test
    void findById_returnsEmpty_whenNotExists() {
        var result = repository.findById(999L);

        assertThat(result).isEmpty();
    }

    @Test
    void findFirstByCanonicalProductKey_returnsMatchingFood() {
        createFood("Oats", "BrandA", null, "key-oats");
        createFood("Rice", "BrandB", null, "key-rice");

        var result = repository.findFirstByCanonicalProductKey("key-oats");

        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("Oats");
    }

    @Test
    void findFirstByCanonicalProductKey_returnsEmpty_whenNotExists() {
        var result = repository.findFirstByCanonicalProductKey("non-existent-key");

        assertThat(result).isEmpty();
    }

    @Test
    void findTop100ByNameContainingIgnoreCase_matchesCaseInsensitive() {
        createFood("Oats & Honey", "BrandA", null, null);
        createFood("oats and more", "BrandB", null, null);
        createFood("Rice", "BrandA", null, null);

        List<FoodEntity> result = repository.findTop100ByNameContainingIgnoreCase("oats");

        assertThat(result).hasSize(2);
        assertThat(result).extracting(FoodEntity::getName)
                .containsExactlyInAnyOrder("Oats & Honey", "oats and more");
    }

    @Test
    void findTop100ByNameContainingIgnoreCase_returnsEmpty_whenNoMatch() {
        List<FoodEntity> result = repository.findTop100ByNameContainingIgnoreCase("xyzxyz");

        assertThat(result).isEmpty();
    }

    @Test
    void findTop100ByBrandContainingIgnoreCase_matchesCaseInsensitive() {
        createFood("Oats", "Morning Farm", null, null);
        createFood("Rice", "morning fresh", null, null);
        createFood("Beans", "Evening Co", null, null);

        List<FoodEntity> result = repository.findTop100ByBrandContainingIgnoreCase("morning");

        assertThat(result).hasSize(2);
        assertThat(result).extracting(FoodEntity::getBrand)
                .containsExactlyInAnyOrder("Morning Farm", "morning fresh");
    }

    @Test
    void findTop100ByBrandContainingIgnoreCase_returnsEmpty_whenNoMatch() {
        List<FoodEntity> result = repository.findTop100ByBrandContainingIgnoreCase("xyzxyz");

        assertThat(result).isEmpty();
    }

    @Test
    void findTop100ByNameContainingIgnoreCaseOrBrandContainingIgnoreCase_matchesEither() {
        createFood("Oats", "SomeBrand", null, null);
        createFood("Rice", "OatBrand", null, null);
        createFood("Beans", "Other", null, null);

        List<FoodEntity> result = repository
                .findTop100ByNameContainingIgnoreCaseOrBrandContainingIgnoreCase("oat", "oat");

        assertThat(result).hasSize(2);
        assertThat(result).extracting(FoodEntity::getName)
                .containsExactlyInAnyOrder("Oats", "Rice");
    }

    @Test
    void findTop100ByNameContainingIgnoreCaseOrBrandContainingIgnoreCase_returnsEmpty_whenNoMatch() {
        List<FoodEntity> result = repository
                .findTop100ByNameContainingIgnoreCaseOrBrandContainingIgnoreCase("xyzxyz", "xyzxyz");

        assertThat(result).isEmpty();
    }
}

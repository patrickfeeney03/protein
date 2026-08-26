package com.example.demo.repositories;

import com.example.demo.entities.FavoriteFoodEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect")
class FavoriteFoodRepositoryTest {

    @Autowired
    private FavoriteFoodRepository repository;

    private FavoriteFoodEntity create(Long userId, Long foodId) {
        return create(userId, foodId, null);
    }

    private FavoriteFoodEntity create(Long userId, Long foodId, Instant createdAt) {
        var entity = new FavoriteFoodEntity();
        entity.setUserId(userId);
        entity.setFoodId(foodId);
        if (createdAt != null) {
            entity.setCreatedAt(createdAt);
        }
        return repository.save(entity);
    }

    @Test
    void existsByUserIdAndFoodId_returnsTrue_whenExists() {
        create(1L, 10L);

        boolean exists = repository.existsByUserIdAndFoodId(1L, 10L);

        assertThat(exists).isTrue();
    }

    @Test
    void existsByUserIdAndFoodId_returnsFalse_whenNotExists() {
        boolean exists = repository.existsByUserIdAndFoodId(99L, 99L);

        assertThat(exists).isFalse();
    }

    @Test
    void deleteByUserIdAndFoodId_removesEntity() {
        create(1L, 10L);

        repository.deleteByUserIdAndFoodId(1L, 10L);

        assertThat(repository.existsByUserIdAndFoodId(1L, 10L)).isFalse();
    }

    @Test
    void deleteByUserIdAndFoodId_doesNotThrow_whenNotExists() {
        repository.deleteByUserIdAndFoodId(99L, 99L);

        assertThat(repository.count()).isZero();
    }

    @Test
    void findAllByUserIdOrderByCreatedAtDesc_returnsFavoritesInOrder() {
        create(1L, 10L, Instant.parse("2025-01-01T00:00:00Z"));
        create(1L, 20L, Instant.parse("2025-06-01T00:00:00Z"));
        create(1L, 30L, Instant.parse("2025-03-01T00:00:00Z"));

        List<FavoriteFoodEntity> result = repository.findAllByUserIdOrderByCreatedAtDesc(1L);

        assertThat(result).hasSize(3);
        assertThat(result.get(0).getFoodId()).isEqualTo(20L);
        assertThat(result.get(1).getFoodId()).isEqualTo(30L);
        assertThat(result.get(2).getFoodId()).isEqualTo(10L);
    }

    @Test
    void findAllByUserIdOrderByCreatedAtDesc_returnsEmpty_whenNoFavorites() {
        List<FavoriteFoodEntity> result = repository.findAllByUserIdOrderByCreatedAtDesc(99L);

        assertThat(result).isEmpty();
    }

    @Test
    void findAllByUserIdOrderByCreatedAtDesc_onlyReturnsMatchingUser() {
        create(1L, 10L);
        create(2L, 20L);

        List<FavoriteFoodEntity> result = repository.findAllByUserIdOrderByCreatedAtDesc(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getFoodId()).isEqualTo(10L);
    }

    @Test
    void deleteAllByFoodId_removesAllMatchingEntities() {
        create(1L, 10L);
        create(2L, 10L);
        create(3L, 20L);

        repository.deleteAllByFoodId(10L);

        assertThat(repository.existsByUserIdAndFoodId(1L, 10L)).isFalse();
        assertThat(repository.existsByUserIdAndFoodId(2L, 10L)).isFalse();
        assertThat(repository.existsByUserIdAndFoodId(3L, 20L)).isTrue();
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void deleteAllByFoodId_doesNotThrow_whenNotExists() {
        repository.deleteAllByFoodId(99L);

        assertThat(repository.count()).isZero();
    }
}

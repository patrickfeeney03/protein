package com.example.demo.repositories;

import com.example.demo.entities.FoodEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FoodRepository extends JpaRepository<FoodEntity, Long> {
    @EntityGraph(attributePaths = "imageUrls")
    java.util.List<FoodEntity> findByProductUrlIsNotNull();
    @Override
    @EntityGraph(attributePaths = "imageUrls")
    java.util.Optional<FoodEntity> findById(Long id);
    java.util.Optional<FoodEntity> findFirstByCanonicalProductKey(String canonicalProductKey);
    java.util.List<FoodEntity> findTop100ByNameContainingIgnoreCaseOrBrandContainingIgnoreCase(String name, String brand);
    java.util.List<FoodEntity> findTop100ByNameContainingIgnoreCase(String name);
    java.util.List<FoodEntity> findTop100ByBrandContainingIgnoreCase(String brand);
}

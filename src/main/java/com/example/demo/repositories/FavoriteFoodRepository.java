package com.example.demo.repositories;

import com.example.demo.entities.FavoriteFoodEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FavoriteFoodRepository extends JpaRepository<FavoriteFoodEntity, Long> {
    boolean existsByUserIdAndFoodId(Long userId, Long foodId);
    void deleteByUserIdAndFoodId(Long userId, Long foodId);
    List<FavoriteFoodEntity> findAllByUserIdOrderByCreatedAtDesc(Long userId);
    void deleteAllByFoodId(Long foodId);
}

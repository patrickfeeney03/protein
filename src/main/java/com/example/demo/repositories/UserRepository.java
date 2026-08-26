package com.example.demo.repositories;

import com.example.demo.entities.UserEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, Long> {
    Optional<UserEntity> findByEmail(String email);

    Optional<UserEntity> findByGoogleSub(String googleSub);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<UserEntity> findFirstById(Long id);

    Optional<UserEntity> findByName(String name);
}

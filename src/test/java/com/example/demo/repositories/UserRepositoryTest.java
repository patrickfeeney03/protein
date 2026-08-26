package com.example.demo.repositories;

import com.example.demo.entities.UserEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect")
class UserRepositoryTest {

    @Autowired
    private UserRepository repository;

    private UserEntity createUser(String email, String name) {
        var entity = new UserEntity();
        entity.setEmail(email);
        entity.setName(name);
        return repository.save(entity);
    }

    @Test
    void findByEmail_returnsUser_whenExists() {
        createUser("alice@example.com", "Alice");

        var result = repository.findByEmail("alice@example.com");

        assertThat(result).isPresent();
        assertThat(result.get().getEmail()).isEqualTo("alice@example.com");
        assertThat(result.get().getName()).isEqualTo("Alice");
    }

    @Test
    void findByEmail_returnsEmpty_whenNotExists() {
        var result = repository.findByEmail("nonexistent@example.com");

        assertThat(result).isEmpty();
    }

    @Test
    void findByEmail_isCaseSensitive() {
        createUser("Alice@Example.com", "Alice");

        var result = repository.findByEmail("alice@example.com");

        assertThat(result).isEmpty();
    }

    @Test
    void findFirstById_withPessimisticLock_returnsUser_whenExists() {
        var user = createUser("carol@example.com", "Carol");

        var result = repository.findFirstById(user.getId());

        assertThat(result).isPresent();
        assertThat(result.get().getEmail()).isEqualTo("carol@example.com");
    }

    @Test
    void findFirstById_withPessimisticLock_returnsEmpty_whenNotExists() {
        var result = repository.findFirstById(999L);

        assertThat(result).isEmpty();
    }

    @Test
    void findByName_returnsUser_whenExists() {
        createUser("dave@example.com", "Dave");

        var result = repository.findByName("Dave");

        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("Dave");
    }

    @Test
    void findByName_returnsEmpty_whenNotExists() {
        var result = repository.findByName("Nobody");

        assertThat(result).isEmpty();
    }

    @Test
    void findByName_returnsUser_whenNameIsNull() {
        createUser("eve@example.com", null);

        var result = repository.findByName(null);

        assertThat(result).isPresent();
        assertThat(result.get().getEmail()).isEqualTo("eve@example.com");
    }

    @Test
    void findByGoogleSub_returnsUser_whenExists() {
        var user = createUser("gina@example.com", "Gina");
        user.setGoogleSub("google-sub-1");
        repository.save(user);

        var result = repository.findByGoogleSub("google-sub-1");

        assertThat(result).isPresent();
        assertThat(result.get().getEmail()).isEqualTo("gina@example.com");
    }

    @Test
    void findByGoogleSub_returnsEmpty_whenNotExists() {
        var result = repository.findByGoogleSub("unknown-sub");

        assertThat(result).isEmpty();
    }
}

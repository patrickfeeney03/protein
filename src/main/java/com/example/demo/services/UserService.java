package com.example.demo.services;

import com.example.demo.entities.UserEntity;
import com.example.demo.repositories.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.Locale;
import java.time.Instant;

@Service
public class UserService {
    public static final int MAX_EMAIL_LENGTH = 254;

    private final UserRepository userRepository;
    private final PersistentTokenRepository persistentTokenRepository;

    public UserService(
            UserRepository userRepository,
            PersistentTokenRepository persistentTokenRepository
    ) {
        this.userRepository = userRepository;
        this.persistentTokenRepository = persistentTokenRepository;
    }

    @Transactional
    public UserEntity findOrCreateGoogleUser(GoogleIdentity identity) {
        if (identity == null || identity.subject() == null || identity.subject().isBlank()
                || identity.email() == null || identity.email().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        if (!identity.emailVerified()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Google email not verified");
        }

        String email = normalizeEmail(identity.email(), "Missing email");

        return userRepository.findByGoogleSub(identity.subject())
                .orElseGet(() -> userRepository.findByEmail(email)
                        .map(user -> linkGoogleAccount(user, identity.subject()))
                        .orElseGet(() -> createGoogleUser(identity, email)));
    }

    @Transactional
    public void invalidateRememberMeTokens(String email) {
        if (email == null || email.isBlank()) {
            return;
        }

        persistentTokenRepository.removeUserTokens(email.trim().toLowerCase(Locale.ROOT));
    }

    public Optional<UserEntity> get(Long userId) {
        return this.userRepository.findById(userId);
    }

    public Optional<UserEntity> getByName(String name) {
        return this.userRepository.findByName(name);
    }

    public Optional<UserEntity> getByEmail(String email) {
        return this.userRepository.findByEmail(email);
    }

    public UserEntity getOrThrow(Long userId) {
        return this.userRepository.findById(userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    public UserEntity.MeResponse me(Long userId) {
        UserEntity user = this.userRepository.findById(userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        return new UserEntity.MeResponse(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.isAdmin(),
                user.isEmailVerified()
        );
    }

    private UserEntity linkGoogleAccount(UserEntity user, String googleSub) {
        user.setGoogleSub(googleSub);
        user.setEmailVerified(true);
        if (user.getEmailVerifiedAt() == null) {
            user.setEmailVerifiedAt(Instant.now());
        }
        return userRepository.save(user);
    }

    private UserEntity createGoogleUser(GoogleIdentity identity, String email) {
        UserEntity user = new UserEntity();
        user.setEmail(email);
        user.setName(identity.name() == null || identity.name().isBlank() ? email : identity.name().trim());
        user.setGoogleSub(identity.subject());
        user.setEmailVerified(true);
        user.setEmailVerifiedAt(Instant.now());
        return userRepository.save(user);
    }

    private String normalizeEmail(String email, String errorMessage) {
        String value = requireText(email, errorMessage);
        if (value.length() > MAX_EMAIL_LENGTH || !value.contains("@")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid email");
        }
        return value.toLowerCase(Locale.ROOT).trim();
    }

    private String requireText(String value, String errorMessage) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, errorMessage);
        }

        return value.trim();
    }
}

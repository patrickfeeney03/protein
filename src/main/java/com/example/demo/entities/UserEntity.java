package com.example.demo.entities;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
public class UserEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    private String name;

    @Column(unique = true)
    private String googleSub;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    private Integer authVersion = 0;

    private Boolean isAdmin = false;
    private Boolean emailVerified = false;
    private Instant emailVerifiedAt;

    public record MeResponse(Long id, String email, String name, boolean isAdmin, boolean emailVerified) {}

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getGoogleSub() {
        return googleSub;
    }

    public void setGoogleSub(String googleSub) {
        this.googleSub = googleSub;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public int getAuthVersion() {
        return authVersion == null ? 0 : authVersion;
    }

    public void setAuthVersion(Integer authVersion) {
        this.authVersion = authVersion;
    }

    public boolean isAdmin() {
        return Boolean.TRUE.equals(isAdmin);
    }

    public void setAdmin(boolean admin) {
        isAdmin = admin;
    }

    public void setAdmin(Boolean admin) {
        isAdmin = admin;
    }

    public boolean isEmailVerified() {
        return Boolean.TRUE.equals(emailVerified);
    }

    public void setEmailVerified(boolean emailVerified) {
        this.emailVerified = emailVerified;
    }

    public void setEmailVerified(Boolean emailVerified) {
        this.emailVerified = emailVerified;
    }

    public Instant getEmailVerifiedAt() {
        return emailVerifiedAt;
    }

    public void setEmailVerifiedAt(Instant emailVerifiedAt) {
        this.emailVerifiedAt = emailVerifiedAt;
    }
}

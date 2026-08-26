package com.example.demo.services;

public record GoogleIdentity(String subject, String email, String name, boolean emailVerified) {
}

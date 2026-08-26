package com.example.demo.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;

@RestController
public class HealthController {

    private final DataSource dataSource;

    public HealthController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @GetMapping("/api/health")
    public ResponseEntity<Void> health() {
        return ResponseEntity.ok().build();
    }

    @GetMapping("/api/health/readiness")
    public ResponseEntity<Void> readiness() {
        try (Connection conn = dataSource.getConnection()) {
            if (conn.isValid(1)) {
                return ResponseEntity.ok().build();
            }
        } catch (Exception e) {
            // fall through to 503
        }
        return ResponseEntity.status(503).build();
    }
}

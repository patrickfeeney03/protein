package com.example.demo.controllers.errors;

import com.example.demo.DTOs.FoodDto;
import com.example.demo.dedup.ExactDuplicateFoodException;
import com.example.demo.dedup.PossibleDuplicateFoodException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleExactDuplicate_returnsConflictResponse() {
        var ex = new ExactDuplicateFoodException("http://example.com/product", 42L);
        var response = handler.handleExactDuplicate(ex);

        assertEquals("EXACT_DUPLICATE", response.code());
        assertEquals("Food with the same product URL key already exists", response.message());
        assertEquals("http://example.com/product", response.canonicalProductKey());
        assertEquals(42L, response.existingFoodId());
        assertTrue(response.candidates().isEmpty());
    }

    @Test
    void handleExactDuplicate_withNullKey() {
        var ex = new ExactDuplicateFoodException(null, 99L);
        var response = handler.handleExactDuplicate(ex);

        assertEquals("EXACT_DUPLICATE", response.code());
        assertNull(response.canonicalProductKey());
        assertEquals(99L, response.existingFoodId());
    }

    @Test
    void handleExactDuplicate_withNullId() {
        var ex = new ExactDuplicateFoodException("key", null);
        var response = handler.handleExactDuplicate(ex);

        assertEquals("EXACT_DUPLICATE", response.code());
        assertEquals("key", response.canonicalProductKey());
        assertNull(response.existingFoodId());
    }

    @Test
    void handlePossibleDuplicate_returnsConflictResponse() {
        var food = new FoodDto();
        food.setId(1L);
        food.setName("Oats");
        var candidate = new DedupCandidateDto(food, 0.95, List.of("same name", "same brand"));
        var ex = new PossibleDuplicateFoodException(List.of(candidate));
        var response = handler.handlePossibleDuplicate(ex);

        assertEquals("POSSIBLE_DUPLICATE", response.code());
        assertEquals("Potential duplicate foods found", response.message());
        assertNull(response.canonicalProductKey());
        assertNull(response.existingFoodId());
        assertEquals(1, response.candidates().size());
        assertEquals(1L, response.candidates().getFirst().food().getId());
        assertEquals("Oats", response.candidates().getFirst().food().getName());
        assertEquals(0.95, response.candidates().getFirst().score());
        assertEquals(List.of("same name", "same brand"), response.candidates().getFirst().matchReasons());
    }

    @Test
    void handlePossibleDuplicate_withEmptyCandidateList() {
        var ex = new PossibleDuplicateFoodException(List.of());
        var response = handler.handlePossibleDuplicate(ex);

        assertEquals("POSSIBLE_DUPLICATE", response.code());
        assertTrue(response.candidates().isEmpty());
    }

    @Test
    void handlePossibleDuplicate_withMultipleCandidates() {
        var food1 = new FoodDto();
        food1.setId(1L);
        var food2 = new FoodDto();
        food2.setId(2L);
        var candidates = List.of(
                new DedupCandidateDto(food1, 0.9, List.of("name match")),
                new DedupCandidateDto(food2, 0.7, List.of("partial match"))
        );
        var ex = new PossibleDuplicateFoodException(candidates);
        var response = handler.handlePossibleDuplicate(ex);

        assertEquals(2, response.candidates().size());
        assertEquals(1L, response.candidates().get(0).food().getId());
        assertEquals(2L, response.candidates().get(1).food().getId());
    }

    @Test
    void handleResponseStatus_returnsStatusAndReason() {
        var ex = new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Google email not verified");
        var response = handler.handleResponseStatus(ex);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals("401 UNAUTHORIZED", response.getBody().get("error"));
        assertEquals("Google email not verified", response.getBody().get("message"));
    }

    @Test
    void handleUnexpected_returnsSafeJson() {
        var ex = new RuntimeException("something went wrong");
        var response = handler.handleUnexpected(ex);

        assertEquals("INTERNAL_SERVER_ERROR", response.get("error"));
        assertEquals("An unexpected error occurred", response.get("message"));
        assertEquals(2, response.size());
    }

    @Test
    void handleUnexpected_withNullMessage() {
        var ex = new RuntimeException((String) null);
        var response = handler.handleUnexpected(ex);

        assertEquals("INTERNAL_SERVER_ERROR", response.get("error"));
        assertEquals("An unexpected error occurred", response.get("message"));
    }

    @Test
    void handleUnexpected_doesNotThrowForAnyException() {
        assertDoesNotThrow(() -> handler.handleUnexpected(new NullPointerException()));
        assertDoesNotThrow(() -> handler.handleUnexpected(new IllegalArgumentException("bad")));
        assertDoesNotThrow(() -> handler.handleUnexpected(new RuntimeException()));
    }
}

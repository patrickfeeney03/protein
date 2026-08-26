package com.example.demo.controllers.errors;

import java.util.List;

public record DedupErrorResponse(
        String code,
        String message,
        String canonicalProductKey,
        Long existingFoodId,
        List<DedupCandidateDto> candidates
) {
}

package com.example.demo.dedup;

import com.example.demo.controllers.errors.DedupCandidateDto;

import java.util.List;

public class PossibleDuplicateFoodException extends RuntimeException {
    private final List<DedupCandidateDto> candidates;

    public PossibleDuplicateFoodException(List<DedupCandidateDto> candidates) {
        super("Possible duplicate food detected");
        this.candidates = List.copyOf(candidates);
    }

    public List<DedupCandidateDto> getCandidates() {
        return candidates;
    }
}

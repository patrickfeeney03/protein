package com.example.demo.dedup;

import java.util.List;

public record DedupCandidate(Long foodId, String name, String brand, double score, List<String> matchReasons) {
}

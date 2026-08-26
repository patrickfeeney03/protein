package com.example.demo.controllers.errors;

import com.example.demo.DTOs.FoodDto;

import java.util.List;

public record DedupCandidateDto(FoodDto food, double score, List<String> matchReasons) {
}

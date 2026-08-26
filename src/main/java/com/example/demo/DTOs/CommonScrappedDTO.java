package com.example.demo.DTOs;

import java.util.List;

public record CommonScrappedDTO (
    List<String> images,
    String brand,
    List<String> categories,
    String name,
    Float price,
    Float weight,
    String link
) {}


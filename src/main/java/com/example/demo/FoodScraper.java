package com.example.demo;

import com.example.demo.DTOs.CommonScrappedDTO;

import java.util.List;

public interface FoodScraper<T> {

    String getStoreName();

    CommonScrappedDTO mapToCommonScrapped(T rawItem);

    List<T> scrapeRaw();

    List<CommonScrappedDTO> getData();

}
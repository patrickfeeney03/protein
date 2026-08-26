package com.example.demo;

import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class MultipartImageNormalizer {

    private MultipartImageNormalizer() {
    }

    public static List<MultipartFile> normalize(List<MultipartFile> images) {
        return normalize(images, null);
    }

    public static List<MultipartFile> normalize(List<MultipartFile> images, MultipartFile image) {
        var normalized = new ArrayList<MultipartFile>();

        if (images != null) {
            images.stream()
                    .filter(Objects::nonNull)
                    .filter(file -> !file.isEmpty())
                    .limit(3)
                    .forEach(normalized::add);
        }

        if (normalized.size() < 3 && image != null && !image.isEmpty()) {
            normalized.add(image);
        }

        return List.copyOf(normalized);
    }
}

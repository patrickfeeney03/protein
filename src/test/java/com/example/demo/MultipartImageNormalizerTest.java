package com.example.demo;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultipartImageNormalizerTest {

    private static MockMultipartFile img(String name) {
        return new MockMultipartFile("images", name, "image/png", new byte[]{1});
    }

    private static MockMultipartFile emptyImg(String name) {
        return new MockMultipartFile("images", name, "image/png", new byte[0]);
    }

    private static List<MultipartFile> listOf(MockMultipartFile... files) {
        return List.of(files);
    }

    // --- normalize(List) ---

    @Test
    void normalizeList_null_returnsEmpty() {
        assertTrue(MultipartImageNormalizer.normalize((List) null).isEmpty());
    }

    @Test
    void normalizeList_empty_returnsEmpty() {
        assertTrue(MultipartImageNormalizer.normalize(List.of()).isEmpty());
    }

    @Test
    void normalizeList_allNulls_returnsEmpty() {
        var list = new ArrayList<MultipartFile>();
        list.add(null);
        list.add(null);
        assertTrue(MultipartImageNormalizer.normalize(list).isEmpty());
    }

    @Test
    void normalizeList_allEmpty_returnsEmpty() {
        assertTrue(MultipartImageNormalizer.normalize(listOf(emptyImg("a.png"), emptyImg("b.png"))).isEmpty());
    }

    @Test
    void normalizeList_filtersNullsAndEmpties() {
        var list = new ArrayList<MultipartFile>();
        list.add(img("a.png"));
        list.add(null);
        list.add(emptyImg("b.png"));
        list.add(img("c.png"));

        var result = MultipartImageNormalizer.normalize(list);
        assertEquals(2, result.size());
        assertEquals("a.png", result.get(0).getOriginalFilename());
        assertEquals("c.png", result.get(1).getOriginalFilename());
    }

    @Test
    void normalizeList_limitsToThree() {
        var result = MultipartImageNormalizer.normalize(listOf(img("a.png"), img("b.png"), img("c.png"), img("d.png"), img("e.png")));
        assertEquals(3, result.size());
        assertEquals("a.png", result.get(0).getOriginalFilename());
        assertEquals("b.png", result.get(1).getOriginalFilename());
        assertEquals("c.png", result.get(2).getOriginalFilename());
    }

    @Test
    void normalizeList_preservesOrder() {
        var result = MultipartImageNormalizer.normalize(listOf(img("x.png"), img("y.png"), img("z.png")));
        assertEquals(3, result.size());
        assertEquals("x.png", result.get(0).getOriginalFilename());
        assertEquals("y.png", result.get(1).getOriginalFilename());
        assertEquals("z.png", result.get(2).getOriginalFilename());
    }

    // --- normalize(List, MultipartFile) ---

    @Test
    void normalizeListAndImage_bothNull_returnsEmpty() {
        assertTrue(MultipartImageNormalizer.normalize(null, null).isEmpty());
    }

    @Test
    void normalizeListAndImage_nullList_validImage_returnsImage() {
        var result = MultipartImageNormalizer.normalize(null, img("single.png"));
        assertEquals(1, result.size());
        assertEquals("single.png", result.get(0).getOriginalFilename());
    }

    @Test
    void normalizeListAndImage_nullList_emptyImage_returnsEmpty() {
        assertTrue(MultipartImageNormalizer.normalize(null, emptyImg("empty.png")).isEmpty());
    }

    @Test
    void normalizeListAndImage_emptyList_nullImage_returnsEmpty() {
        assertTrue(MultipartImageNormalizer.normalize(List.of(), null).isEmpty());
    }

    @Test
    void normalizeListAndImage_emptyList_validImage_returnsImage() {
        var result = MultipartImageNormalizer.normalize(List.of(), img("single.png"));
        assertEquals(1, result.size());
        assertEquals("single.png", result.get(0).getOriginalFilename());
    }

    @Test
    void normalizeListAndImage_emptyList_emptyImage_returnsEmpty() {
        assertTrue(MultipartImageNormalizer.normalize(List.of(), emptyImg("empty.png")).isEmpty());
    }

    @Test
    void normalizeListAndImage_fillsRemainingSlot() {
        var result = MultipartImageNormalizer.normalize(listOf(img("a.png"), img("b.png")), img("c.png"));

        assertEquals(3, result.size());
        assertEquals("a.png", result.get(0).getOriginalFilename());
        assertEquals("b.png", result.get(1).getOriginalFilename());
        assertEquals("c.png", result.get(2).getOriginalFilename());
    }

    @Test
    void normalizeListAndImage_doesNotExceedLimit() {
        var result = MultipartImageNormalizer.normalize(listOf(img("a.png"), img("b.png"), img("c.png")), img("d.png"));

        assertEquals(3, result.size());
        assertEquals("a.png", result.get(0).getOriginalFilename());
        assertEquals("b.png", result.get(1).getOriginalFilename());
        assertEquals("c.png", result.get(2).getOriginalFilename());
    }

    @Test
    void normalizeListAndImage_emptyImageNotAddedToSlot() {
        var result = MultipartImageNormalizer.normalize(listOf(img("a.png")), emptyImg("empty.png"));

        assertEquals(1, result.size());
        assertEquals("a.png", result.get(0).getOriginalFilename());
    }

    @Test
    void normalizeListAndImage_nullImageNotAdded() {
        var result = MultipartImageNormalizer.normalize(listOf(img("a.png")), null);

        assertEquals(1, result.size());
        assertEquals("a.png", result.get(0).getOriginalFilename());
    }

    @Test
    void normalizeListAndImage_listFull_emptyImageNotAdded() {
        var result = MultipartImageNormalizer.normalize(listOf(img("a.png"), img("b.png"), img("c.png")), emptyImg("empty.png"));

        assertEquals(3, result.size());
    }

    @Test
    void normalizeListAndImage_mixedNullsEmptiesInList() {
        var list = new ArrayList<MultipartFile>();
        list.add(null);
        list.add(img("a.png"));
        list.add(emptyImg("b.png"));
        list.add(img("c.png"));
        list.add(null);

        var result = MultipartImageNormalizer.normalize(list, img("d.png"));

        assertEquals(3, result.size());
        assertEquals("a.png", result.get(0).getOriginalFilename());
        assertEquals("c.png", result.get(1).getOriginalFilename());
        assertEquals("d.png", result.get(2).getOriginalFilename());
    }

    @Test
    void normalizeListAndImage_returnsUnmodifiable() {
        var result = MultipartImageNormalizer.normalize(listOf(img("a.png")), null);
        try {
            result.add(img("b.png"));
        } catch (UnsupportedOperationException e) {
            // expected
        }
    }
}

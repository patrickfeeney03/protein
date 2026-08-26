package com.example.demo.services;

import com.example.demo.AldiPriceFetcher;
import com.example.demo.DunnesPriceFetcher;
import com.example.demo.LidlPriceFetcher;
import com.example.demo.TescoPriceFetcher;
import com.example.demo.controllers.errors.DedupCandidateDto;
import com.example.demo.dedup.DedupCandidate;
import com.example.demo.dedup.ExactDuplicateFoodException;
import com.example.demo.dedup.FoodDedupService;
import com.example.demo.dedup.PossibleDuplicateFoodException;
import com.example.demo.entities.FoodEntity;
import com.example.demo.entities.UserEntity;
import com.example.demo.repositories.FavoriteFoodRepository;
import com.example.demo.repositories.FoodRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FoodServiceDedupTest {

    @Mock
    private FoodRepository foodRepository;
    @Mock
    private UserService userService;
    @Mock
    private AldiPriceFetcher aldiPriceFetcher;
    @Mock
    private LidlPriceFetcher lidlPriceFetcher;
    @Mock
    private DunnesPriceFetcher dunnesPriceFetcher;
    @Mock
    private TescoPriceFetcher tescoPriceFetcher;
    @Mock
    private CommentService commentService;
    @Mock
    private FavoriteFoodRepository favoriteFoodRepository;
    @Mock
    private FoodDedupService foodDedupService;

    @InjectMocks
    private FoodService foodService;

    @Test
    void addFood_blocksExactDuplicateByCanonicalKey() {
        var request = baseRequest("https://www.tesco.ie/groceries/en-IE/products/123456789", false);
        var existing = new FoodEntity();
        existing.setId(99L);

        when(foodDedupService.computeCanonicalProductKey(request.productUrl())).thenReturn(Optional.of("tesco:123456789"));
        when(foodDedupService.findExactDuplicate("tesco:123456789")).thenReturn(Optional.of(existing));

        var ex = assertThrows(ExactDuplicateFoodException.class, () -> foodService.addFood(request, 1L));

        assertEquals("tesco:123456789", ex.getCanonicalProductKey());
        assertEquals(99L, ex.getExistingFoodId());
        verify(foodRepository, never()).save(any());
    }

    @Test
    void addFood_blocksExactDuplicateByCanonicalKey_evenWhenForceCreateIsTrue() {
        var request = baseRequest("https://www.tesco.ie/groceries/en-IE/products/123456789", true);
        var existing = new FoodEntity();
        existing.setId(123L);

        when(foodDedupService.computeCanonicalProductKey(request.productUrl())).thenReturn(Optional.of("tesco:123456789"));
        when(foodDedupService.findExactDuplicate("tesco:123456789")).thenReturn(Optional.of(existing));

        var ex = assertThrows(ExactDuplicateFoodException.class, () -> foodService.addFood(request, 1L));

        assertEquals("tesco:123456789", ex.getCanonicalProductKey());
        assertEquals(123L, ex.getExistingFoodId());
        verify(foodDedupService, never()).findLikelyDuplicates(any(), any(), any(), any(), any(), any(), any());
        verify(foodRepository, never()).save(any());
    }

    @Test
    void addFood_blocksPossibleDuplicateWhenForceCreateIsFalse() {
        var request = baseRequest(null, false);
        var existing = new FoodEntity();
        existing.setId(42L);
        existing.setName("Chicken Breast");
        existing.setBrand("Tesco");
        existing.setUserId(1L);

        when(foodDedupService.computeCanonicalProductKey(null)).thenReturn(Optional.empty());
        when(foodDedupService.findLikelyDuplicates(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(new DedupCandidate(42L, "Chicken Breast", "Tesco", 0.94d, List.of("name", "macros"))));
        when(foodDedupService.isPossibleDuplicate(any())).thenReturn(true);
        when(foodRepository.findAllById(List.of(42L))).thenReturn(List.of(existing));

        var user = new UserEntity();
        user.setId(1L);
        user.setAdmin(false);
        when(userService.getOrThrow(1L)).thenReturn(user);

        var ex = assertThrows(PossibleDuplicateFoodException.class, () -> foodService.addFood(request, 1L));

        assertEquals(1, ex.getCandidates().size());
        DedupCandidateDto candidate = ex.getCandidates().getFirst();
        assertEquals(42L, candidate.food().getId());
        assertEquals("Chicken Breast", candidate.food().getName());
        verify(foodRepository, never()).save(any());
    }

    @Test
    void addFood_allowsForceCreateForFuzzyDuplicates() {
        var request = baseRequest(null, true);

        when(foodDedupService.computeCanonicalProductKey(null)).thenReturn(Optional.empty());

        when(foodRepository.save(any(FoodEntity.class))).thenAnswer(invocation -> {
            FoodEntity entity = invocation.getArgument(0);
            entity.setId(7L);
            return entity;
        });

        var user = new UserEntity();
        user.setId(1L);
        user.setAdmin(false);
        when(userService.getOrThrow(1L)).thenReturn(user);

        var dto = foodService.addFood(request, 1L);

        assertEquals(7L, dto.getId());

        var captor = ArgumentCaptor.forClass(FoodEntity.class);
        verify(foodRepository).save(captor.capture());
        assertNull(captor.getValue().getCanonicalProductKey());

        verify(foodDedupService, never()).findLikelyDuplicates(any(), any(), any(), any(), any(), any(), any());
    }

    private FoodEntity.FoodRequest baseRequest(String productUrl, boolean forceCreate) {
        return new FoodEntity.FoodRequest(
                "Chicken Breast",
                "Tesco",
                200f,
                FoodEntity.Unit.G,
                8.99f,
                4f,
                240f,
                48f,
                0f,
                4f,
                "",
                "Tesco",
                null,
                productUrl,
                forceCreate
        );
    }
}

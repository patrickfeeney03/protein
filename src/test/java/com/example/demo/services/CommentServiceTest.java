package com.example.demo.services;

import com.example.demo.DTOs.CommentDto;
import com.example.demo.entities.CommentEntity;
import com.example.demo.entities.UserEntity;
import com.example.demo.repositories.CommentRepository;
import com.example.demo.repositories.FoodRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    @Mock
    private CommentRepository commentRepository;
    @Mock
    private FoodRepository foodRepository;
    @Mock
    private UserService userService;

    @InjectMocks
    private CommentService commentService;

    @Test
    void addComment_requiresUserId() {
        var request = new CommentDto.CommentRequest("hi", 1L);
        var ex = assertThrows(ResponseStatusException.class, () -> commentService.addComment(request, null));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
    }

    @Test
    void addComment_rejectsMissingRequest() {
        var ex = assertThrows(ResponseStatusException.class, () -> commentService.addComment(null, 3L));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void addComment_rejectsMissingFoodId() {
        var ex = assertThrows(ResponseStatusException.class, () -> commentService.addComment(new CommentDto.CommentRequest("hi", null), 3L));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void addComment_rejectsBlankContent() {
        var ex = assertThrows(ResponseStatusException.class, () -> commentService.addComment(new CommentDto.CommentRequest("   ", 1L), 3L));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void addComment_requiresExistingFood() {
        when(foodRepository.existsById(9L)).thenReturn(false);

        var request = new CommentDto.CommentRequest("hi", 9L);
        var ex = assertThrows(ResponseStatusException.class, () -> commentService.addComment(request, 3L));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void addComment_persistsAndReturnsDto() {
        when(foodRepository.existsById(9L)).thenReturn(true);
        var user = new UserEntity();
        user.setId(3L);
        user.setName("Alice");
        when(userService.getOrThrow(3L)).thenReturn(user);

        var saved = new CommentEntity();
        saved.setId(99L);
        saved.setUserId(3L);
        saved.setFoodId(9L);
        saved.setContent("Great food");
        saved.setUserName("Alice");

        when(commentRepository.save(any(CommentEntity.class))).thenReturn(saved);

        var dto = commentService.addComment(new CommentDto.CommentRequest("Great food", 9L), 3L);

        assertEquals(99L, dto.getId());
        assertEquals(3L, dto.getUserId());
        assertEquals(9L, dto.getFoodId());
        assertEquals("Great food", dto.getContent());

        ArgumentCaptor<CommentEntity> captor = ArgumentCaptor.forClass(CommentEntity.class);
        verify(commentRepository).save(captor.capture());
        assertEquals("Alice", captor.getValue().getUserName());
    }

    @Test
    void getCommentsForFood_mapsEntitiesToDtos() {
        var entity = new CommentEntity();
        entity.setId(5L);
        entity.setUserId(7L);
        entity.setFoodId(2L);
        entity.setContent("c");
        entity.setUserName("u");
        when(commentRepository.findAllByFoodId(2L)).thenReturn(List.of(entity));

        var dtos = commentService.getCommentsForFood(2L);
        assertEquals(1, dtos.size());
        assertEquals(5L, dtos.getFirst().getId());
        assertEquals("c", dtos.getFirst().getContent());
        assertNull(dtos.getFirst().getUserId());
    }

    @Test
    void deleteComment_forbidsWhenUserDoesNotOwn() {
        var user = new UserEntity();
        user.setId(1L);
        when(userService.get(1L)).thenReturn(Optional.of(user));

        var comment = new CommentEntity();
        comment.setId(10L);
        comment.setUserId(2L);
        when(commentRepository.findById(10L)).thenReturn(Optional.of(comment));

        var ex = assertThrows(ResponseStatusException.class, () -> commentService.deleteComment(10L, 1L));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        verify(commentRepository, never()).deleteById(any());
    }

    @Test
    void deleteComment_requiresUserId() {
        var ex = assertThrows(ResponseStatusException.class, () -> commentService.deleteComment(10L, null));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        verifyNoInteractions(userService, commentRepository);
    }

    @Test
    void deleteComment_deletesWhenOwner() {
        var user = new UserEntity();
        user.setId(1L);
        when(userService.get(1L)).thenReturn(Optional.of(user));

        var comment = new CommentEntity();
        comment.setId(10L);
        comment.setUserId(1L);
        when(commentRepository.findById(10L)).thenReturn(Optional.of(comment));

        commentService.deleteComment(10L, 1L);

        verify(commentRepository).deleteById(10L);
    }
}

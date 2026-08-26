package com.example.demo.services;

import com.example.demo.DTOs.CommentDto;
import com.example.demo.entities.CommentEntity;
import com.example.demo.repositories.CommentRepository;
import com.example.demo.repositories.FoodRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class CommentService {
    private final CommentRepository commentRepository;
    private final FoodRepository foodRepository;
    private final UserService userService;

    public CommentService(CommentRepository commentRepository, FoodRepository foodRepository, UserService userService) {
        this.commentRepository = commentRepository;
        this.foodRepository = foodRepository;
        this.userService = userService;
    }

    public CommentDto addComment(CommentDto.CommentRequest request, Long userId) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No user Id");
        }
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing comment request");
        }
        if (request.foodId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing food id");
        }
        if (request.content() == null || request.content().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing comment content");
        }

        if (!this.foodRepository.existsById(request.foodId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }

        var user = this.userService.getOrThrow(userId);

        var comment = new CommentEntity();
        comment.setUserId(userId);
        comment.setFoodId(request.foodId());
        comment.setContent(request.content());
        comment.setUserName(user.getName());

        var persistedComment = this.commentRepository.save(comment);

        return toDto(persistedComment, true);
    }

    public List<CommentDto> getCommentsForFood(Long foodId) {
        var comments = this.commentRepository.findAllByFoodId(foodId);
        return comments.stream()
                .map(comment -> toDto(comment, false))
                .toList();
    }

    private Optional<CommentEntity> getCommentEntity(Long id) {
        return this.commentRepository.findById(id);
    }

    public void deleteComment(Long commentId, Long userId) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No user Id");
        }
        var user = this.userService.get(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        var comment = getCommentEntity(commentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        if (!Objects.equals(comment.getUserId(), user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        this.commentRepository.deleteById(comment.getId());
    }

    public void deleteAllForFood(Long foodId) {
        this.commentRepository.deleteAllByFoodId(foodId);
    }

    private CommentDto toDto(CommentEntity e, boolean includeUserId) {
        var dto = new CommentDto();

        dto.setId(e.getId());
        if (includeUserId) {
            dto.setUserId(e.getUserId());
        }
        dto.setFoodId(e.getFoodId());
        dto.setContent(e.getContent());
        dto.setCreatedAt(e.getCreatedAt());
        dto.setUserName(e.getUserName());

        return dto;
    }
}

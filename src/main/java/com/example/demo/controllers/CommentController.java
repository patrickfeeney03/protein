package com.example.demo.controllers;

import com.example.demo.DTOs.CommentDto;
import com.example.demo.entities.UserEntity;
import com.example.demo.services.CommentService;
import com.example.demo.services.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/comment")
public class CommentController {
    private final CommentService commentService;
    private final UserService userService;

    public CommentController(CommentService commentService, UserService userService) {
        this.commentService = commentService;
        this.userService = userService;
    }

    @PostMapping
    public CommentDto addComment(
            @Valid @RequestBody CommentDto.CommentRequest request,
            Authentication authentication
    ) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing comment request");
        }

        Long userId = getUserIdFromAuthentication(authentication);

        return this.commentService.addComment(request, userId);
    }

    @GetMapping("/food/{foodId}")
    public List<CommentDto> getCommentsForFood(@PathVariable Long foodId) {
        return this.commentService.getCommentsForFood(foodId);
    }

    @DeleteMapping("/{id}")
    public void deleteComment(@PathVariable Long id, Authentication authentication) {
        Long userId = getUserIdFromAuthentication(authentication);

        this.commentService.deleteComment(id, userId);
    }

    private Long getUserIdFromAuthentication(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        var email = authentication.getName();
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        return this.userService.getByEmail(email)
                .map(UserEntity::getId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }
}

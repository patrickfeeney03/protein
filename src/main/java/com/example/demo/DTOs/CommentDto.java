package com.example.demo.DTOs;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class CommentDto {
    private Long id;
    private Long userId;
    private Long foodId;
    private String content;
    private String userName;
    private Instant createdAt;

    public record CommentRequest(@NotBlank @jakarta.validation.constraints.Size(max = 2000) String content,
                                 @NotNull Long foodId) {
    }

    public CommentDto(Long id, Long userId, Long foodId, String content, Instant createdAt, String userName) {
        this.id = id;
        this.userId = userId;
        this.foodId = foodId;
        this.content = content;
        this.createdAt = createdAt;
        this.userName = userName;
    }

    public CommentDto() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getFoodId() {
        return foodId;
    }

    public void setFoodId(Long foodId) {
        this.foodId = foodId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }
}

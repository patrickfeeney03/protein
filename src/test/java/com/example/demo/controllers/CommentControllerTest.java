package com.example.demo.controllers;

import com.example.demo.DTOs.CommentDto;
import com.example.demo.entities.UserEntity;
import com.example.demo.services.CommentService;
import com.example.demo.services.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CommentControllerTest {
    private final CommentService commentService = mock(CommentService.class);
    private final UserService userService = mock(UserService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new CommentController(commentService, userService))
                .build();
    }

    @Test
    void addComment_resolvesUserIdFromAuthenticationName() throws Exception {
        var user = new UserEntity();
        user.setId(5L);
        when(userService.getByEmail("patrick@example.com")).thenReturn(Optional.of(user));

        var comment = new CommentDto();
        comment.setId(9L);
        comment.setFoodId(22L);
        comment.setUserId(5L);
        comment.setContent("Nice");
        when(commentService.addComment(any(CommentDto.CommentRequest.class), eq(5L))).thenReturn(comment);

        var request = new CommentDto.CommentRequest("Nice", 22L);

        mockMvc.perform(post("/api/comment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request))
                        .principal(new UsernamePasswordAuthenticationToken("patrick@example.com", "n/a", List.of())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(9L))
                .andExpect(jsonPath("$.userId").value(5L));

        verify(commentService).addComment(any(CommentDto.CommentRequest.class), eq(5L));
    }

    @Test
    void addComment_withoutAuthentication_returnsUnauthorized() throws Exception {
        var request = new CommentDto.CommentRequest("Nice", 22L);

        mockMvc.perform(post("/api/comment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isUnauthorized());

        verify(commentService, never()).addComment(any(), any());
    }

    @Test
    void addComment_rejectsNullRequestBodyBeforeServiceCall() throws Exception {
        mockMvc.perform(post("/api/comment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("null")
                        .principal(new UsernamePasswordAuthenticationToken("patrick@example.com", "n/a", List.of())))
                .andExpect(status().isBadRequest());

        verify(commentService, never()).addComment(any(), any());
    }

    @Test
    void addComment_rejectsAnonymousPrincipal() throws Exception {
        var request = new CommentDto.CommentRequest("Nice", 22L);

        mockMvc.perform(post("/api/comment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request))
                        .principal(new AnonymousAuthenticationToken(
                                "key",
                                "anonymousUser",
                                List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")))))
                .andExpect(status().isUnauthorized());

        verify(commentService, never()).addComment(any(), any());
    }

    @Test
    void getCommentsForFood_allowsAnonymousAccess() throws Exception {
        var comment = new CommentDto();
        comment.setId(10L);
        comment.setFoodId(22L);
        comment.setContent("Public");
        when(commentService.getCommentsForFood(22L)).thenReturn(List.of(comment));

        mockMvc.perform(get("/api/comment/food/22"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(10L))
                .andExpect(jsonPath("$[0].content").value("Public"));

        verify(commentService).getCommentsForFood(22L);
    }

    @Test
    void deleteComment_deletesComment() throws Exception {
        var user = new UserEntity();
        user.setId(5L);
        when(userService.getByEmail("patrick@example.com")).thenReturn(Optional.of(user));

        mockMvc.perform(delete("/api/comment/99")
                        .principal(new UsernamePasswordAuthenticationToken("patrick@example.com", "n/a", List.of())))
                .andExpect(status().isOk());

        verify(commentService).deleteComment(99L, 5L);
    }

    @Test
    void deleteComment_withoutAuthentication_returnsUnauthorized() throws Exception {
        mockMvc.perform(delete("/api/comment/99"))
                .andExpect(status().isUnauthorized());

        verify(commentService, never()).deleteComment(any(), any());
    }

    @Test
    void deleteComment_anonymousPrincipal_returnsUnauthorized() throws Exception {
        mockMvc.perform(delete("/api/comment/99")
                        .principal(new AnonymousAuthenticationToken(
                                "key",
                                "anonymousUser",
                                List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")))))
                .andExpect(status().isUnauthorized());

        verify(commentService, never()).deleteComment(any(), any());
    }
}

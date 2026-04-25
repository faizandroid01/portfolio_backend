package com.blogplatform.controller;

import com.blogplatform.dto.*;
import com.blogplatform.model.Author;
import com.blogplatform.repository.AuthorRepository;
import com.blogplatform.service.AuthorService;
import com.blogplatform.service.BlogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class BlogController {

    private final BlogService blogService;
    private final AuthorService authorService;


    // ─── Posts ────────────────────────────────────────────────

    @GetMapping("/posts")
    public Page<PostSummaryResponse> listPosts(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size,
        @RequestParam(required = false) String tag,
        @RequestParam(required = false) String search
    ) {
        return blogService.listPosts(page, size, tag, search);
    }

    @GetMapping("/authors")
    public ResponseEntity<List<Author>> listAuthors() {
        return ResponseEntity.ok(authorService.getAllAuthors());
    }

    @GetMapping("/posts/{slug}")
    public PostDetailResponse getPost(@PathVariable String slug) {
        return blogService.getBySlug(slug);
    }

    @PostMapping("/posts")
    @ResponseStatus(HttpStatus.CREATED)
    public PostDetailResponse createPost(@Valid @RequestBody CreatePostRequest request) {
        return blogService.createPost(request);
    }

    @PutMapping("/posts/{id}")
    public PostDetailResponse updatePost(
        @PathVariable UUID id,
        @RequestBody UpdatePostRequest request
    ) {
        return blogService.updatePost(id, request);
    }

    @DeleteMapping("/posts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePost(@PathVariable UUID id) {
        blogService.deletePost(id);
    }

    @PostMapping("/posts/{id}/like")
    public Map<String, Integer> likePost(@PathVariable UUID id) {
        return Map.of("likes", blogService.likePost(id));
    }

    // ─── Comments ────────────────────────────────────────────

    @PostMapping("/posts/{postId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public PostDetailResponse.CommentResponse addComment(
        @PathVariable UUID postId,
        @Valid @RequestBody CreateCommentRequest request
    ) {
        return blogService.addComment(postId, request);
    }

    @DeleteMapping("/posts/{postId}/comments/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteComment(@PathVariable UUID postId, @PathVariable UUID commentId) {
        blogService.deleteComment(postId, commentId);
    }

    @PostMapping("/comments/{commentId}/like")
    public Map<String, Integer> likeComment(@PathVariable UUID commentId) {
        return Map.of("likes", blogService.likeComment(commentId));
    }

    // ─── Tags ────────────────────────────────────────────────

    @GetMapping("/tags")
    public List<String> getAllTags() {
        return blogService.getAllTags();
    }

    // ─── Exception handling ──────────────────────────────────

    @ExceptionHandler(java.util.NoSuchElementException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(java.util.NoSuchElementException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(Map.of("error", ex.getMessage()));
    }
}

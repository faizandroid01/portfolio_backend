package com.blogplatform.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data @Builder
public class PostDetailResponse {
    private UUID id;
    private String title;
    private String slug;
    private String authorName;
    private String authorAvatar;
    private LocalDate date;
    private String readingTime;
    private String thumbnail;
    private String description;
    private int likes;
    private long commentsCount;
    private List<String> hashtags;
    private List<SectionResponse> sections;
    private List<CommentResponse> comments;

    @Data @Builder
    public static class SectionResponse {
        private UUID id;
        private int sortOrder;
        private String type;
        private Map<String, Object> content;
    }

    @Data @Builder
    public static class CommentResponse {
        private UUID id;
        private String username;
        private String avatar;
        private String text;
        private int likes;
        private String createdAt;
    }
}

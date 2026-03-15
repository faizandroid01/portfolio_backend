package com.blogplatform.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data @Builder
public class PostSummaryResponse {
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
}

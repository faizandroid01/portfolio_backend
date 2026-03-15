package com.blogplatform.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class UpdatePostRequest {
    private String title;
    private String description;
    private String thumbnail;
    private String readingTime;
    private Boolean isPublished;
    private List<String> hashtags;
    private List<CreatePostRequest.SectionInput> sections;
}

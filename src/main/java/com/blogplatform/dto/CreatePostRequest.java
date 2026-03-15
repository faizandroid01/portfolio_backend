package com.blogplatform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
public class CreatePostRequest {

    @NotBlank
    private String title;

    @NotBlank
    private String slug;

    @NotNull
    private UUID authorId;

    private String thumbnail;
    private String description;
    private String readingTime;
    private List<String> hashtags;
    private List<SectionInput> sections;

    @Data
    public static class SectionInput {
        @NotBlank
        private String type;
        private Map<String, Object> content;
    }
}

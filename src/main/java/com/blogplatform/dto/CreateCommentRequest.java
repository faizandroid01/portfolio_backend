package com.blogplatform.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateCommentRequest {
    @NotBlank
    private String username;
    private String avatar;
    @NotBlank
    private String text;
}

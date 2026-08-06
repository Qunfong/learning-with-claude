package com.bookscanner.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SaveBookRequest {
    @NotBlank
    private String title;
    private String author;
    private String isbn;
    private String coverUrl;
    private String description;
}

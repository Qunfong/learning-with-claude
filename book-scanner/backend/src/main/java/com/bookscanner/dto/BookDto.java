package com.bookscanner.dto;

import com.bookscanner.domain.Book;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class BookDto {
    private Long id;
    private String title;
    private String author;
    private String isbn;
    private String coverUrl;
    private String description;
    private Instant addedAt;

    public static BookDto from(Book book) {
        return BookDto.builder()
                .id(book.getId())
                .title(book.getTitle())
                .author(book.getAuthor())
                .isbn(book.getIsbn())
                .coverUrl(book.getCoverUrl())
                .description(book.getDescription())
                .addedAt(book.getAddedAt())
                .build();
    }
}

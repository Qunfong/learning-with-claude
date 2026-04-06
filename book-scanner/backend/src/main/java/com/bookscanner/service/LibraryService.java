package com.bookscanner.service;

import com.bookscanner.domain.Book;
import com.bookscanner.domain.BookRepository;
import com.bookscanner.dto.BookDto;
import com.bookscanner.dto.SaveBookRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class LibraryService {

    private final BookRepository bookRepository;

    @Transactional(readOnly = true)
    public List<BookDto> getAllBooks() {
        return bookRepository.findAll()
                .stream()
                .map(BookDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<BookDto> getBookById(Long id) {
        return bookRepository.findById(id).map(BookDto::from);
    }

    public BookDto saveBook(SaveBookRequest request) {
        Book book = Book.builder()
                .title(request.getTitle())
                .author(request.getAuthor())
                .isbn(request.getIsbn())
                .coverUrl(request.getCoverUrl())
                .description(request.getDescription())
                .build();
        return BookDto.from(bookRepository.save(book));
    }

    public void deleteBook(Long id) {
        bookRepository.deleteById(id);
    }
}

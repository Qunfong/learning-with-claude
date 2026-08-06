package com.bookscanner.controller;

import com.bookscanner.dto.BookDto;
import com.bookscanner.dto.SaveBookRequest;
import com.bookscanner.service.LibraryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/books")
@RequiredArgsConstructor
public class LibraryController {

    private final LibraryService libraryService;

    @GetMapping
    public List<BookDto> getAllBooks() {
        return libraryService.getAllBooks();
    }

    @GetMapping("/{id}")
    public ResponseEntity<BookDto> getBook(@PathVariable Long id) {
        return libraryService.getBookById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<BookDto> saveBook(@RequestBody @Valid SaveBookRequest request) {
        BookDto saved = libraryService.saveBook(request);
        return ResponseEntity.status(201).body(saved);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteBook(@PathVariable Long id) {
        libraryService.deleteBook(id);
        return ResponseEntity.ok(Map.of("message", "Boek verwijderd"));
    }
}

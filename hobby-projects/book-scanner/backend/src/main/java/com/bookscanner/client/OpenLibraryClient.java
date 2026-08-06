package com.bookscanner.client;

import com.bookscanner.dto.BookRecognizedEvent.BookCandidate;
import tools.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;

/**
 * Client for the Open Library Search API.
 * Documentation: https://openlibrary.org/dev/docs/api#anchor_searchapi
 *
 * Learning note: We use WebClient (reactive) instead of RestTemplate (blocking).
 * For a simple synchronous call we use .block() to bridge into
 * imperative code. In a fully reactive application you would return Mono instead.
 *
 * The API returns the top-N results. We take the first 3 as candidates
 * so the user can choose which book is correct.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OpenLibraryClient {

    private static final String BASE_URL = "https://openlibrary.org";
    private static final int MAX_CANDIDATES = 3;

    private final WebClient.Builder webClientBuilder;

    /**
     * Searches for books based on a query (free text: title, author, or ISBN).
     *
     * @param query the search text (e.g. coming from OCR)
     * @return top-3 book results found, empty if no results
     */
    public List<BookCandidate> search(String query) {
        log.info("Open Library search query: '{}'", query);

        try {
            JsonNode result = webClientBuilder.baseUrl(BASE_URL)
                    .build()
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/search.json")
                            .queryParam("q", query)
                            .queryParam("limit", MAX_CANDIDATES)
                            .queryParam("fields", "title,author_name,isbn,cover_i,first_sentence")
                            .build())
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (result == null || !result.has("docs")) {
                return List.of();
            }

            List<BookCandidate> candidates = new ArrayList<>();
            for (JsonNode doc : result.get("docs")) {
                BookCandidate candidate = BookCandidate.builder()
                        .title(getText(doc, "title"))
                        .author(getFirstArrayElement(doc, "author_name"))
                        .isbn(getFirstArrayElement(doc, "isbn"))
                        .coverUrl(buildCoverUrl(doc))
                        .description(getFirstArrayElement(doc, "first_sentence"))
                        .build();
                candidates.add(candidate);
            }

            log.info("{} candidate(s) found for query '{}'", candidates.size(), query);
            return candidates;

        } catch (Exception e) {
            log.error("Error fetching Open Library data for query '{}'", query, e);
            return List.of();
        }
    }

    private String getText(JsonNode node, String field) {
        return node.has(field) ? node.get(field).asText() : null;
    }

    private String getFirstArrayElement(JsonNode node, String field) {
        if (node.has(field) && node.get(field).isArray() && node.get(field).size() > 0) {
            return node.get(field).get(0).asText();
        }
        return null;
    }

    private String buildCoverUrl(JsonNode doc) {
        if (doc.has("cover_i")) {
            long coverId = doc.get("cover_i").asLong();
            return "https://covers.openlibrary.org/b/id/" + coverId + "-M.jpg";
        }
        return null;
    }
}

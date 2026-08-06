package com.example.demo.flight.controller;

import com.example.demo.flight.client.FlightApiException;
import com.example.demo.flight.model.FlightOffer;
import com.example.demo.flight.service.FlightSearchService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/flights")
public class FlightSearchController {

    // IATA airport codes are 3 uppercase letters
    private static final Pattern IATA = Pattern.compile("^[A-Z]{3}$");

    private final FlightSearchService flightSearchService;

    public FlightSearchController(FlightSearchService flightSearchService) {
        this.flightSearchService = flightSearchService;
    }

    @GetMapping("/search")
    public ResponseEntity<?> search(
            @RequestParam String origin,
            @RequestParam String destination,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        // Task 4.2: validate IATA codes
        if (!IATA.matcher(origin).matches()) {
            return ResponseEntity.badRequest().body(
                    Map.of("error", "invalid_request", "error_description", "origin must be a valid 3-letter IATA code"));
        }
        if (!IATA.matcher(destination).matches()) {
            return ResponseEntity.badRequest().body(
                    Map.of("error", "invalid_request", "error_description", "destination must be a valid 3-letter IATA code"));
        }

        try {
            // Task 4.4: returns empty list if no flights found (handled in service/client)
            List<FlightOffer> offers = flightSearchService.search(origin, destination, from, to);
            return ResponseEntity.ok(offers);
        } catch (FlightApiException e) {
            // Task 4.3: HTTP 502 on external API error
            return ResponseEntity.status(502).body(
                    Map.of("error", "external_api_error", "error_description", e.getMessage()));
        }
    }
}

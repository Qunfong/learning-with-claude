package com.example.demo.flight.model;

import java.math.BigDecimal;

public record FlightOffer(
        String departureDate,
        String departureTime,
        String arrivalTime,
        String airline,
        String flightNumber,
        BigDecimal price,
        String currency,
        String bookingUrl
) {}

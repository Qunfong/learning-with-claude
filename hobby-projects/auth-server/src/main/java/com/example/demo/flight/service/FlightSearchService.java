package com.example.demo.flight.service;

import com.example.demo.flight.client.DuffelApiClient;
import com.example.demo.flight.model.FlightOffer;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class FlightSearchService {

    private final DuffelApiClient duffelApiClient;

    public FlightSearchService(DuffelApiClient duffelApiClient) {
        this.duffelApiClient = duffelApiClient;
    }

    /**
     * Search flights for a route over a date range.
     * Defaults: from = today, to = today + 14 days.
     * Results sorted by price ascending.
     */
    public List<FlightOffer> search(String origin, String destination, LocalDate from, LocalDate to) {
        if (from == null) from = LocalDate.now();
        if (to == null) to = from.plusDays(14);

        List<FlightOffer> allOffers = new ArrayList<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            allOffers.addAll(duffelApiClient.searchFlights(origin, destination, date));
        }

        allOffers.sort(Comparator.comparing(FlightOffer::price));
        return allOffers;
    }
}
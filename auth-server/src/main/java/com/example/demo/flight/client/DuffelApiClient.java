package com.example.demo.flight.client;

import com.example.demo.flight.config.DuffelProperties;
import com.example.demo.flight.model.FlightOffer;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class DuffelApiClient {

    private final RestClient restClient;

    public DuffelApiClient(DuffelProperties props) {
        this.restClient = RestClient.builder()
                .baseUrl(props.getBaseUrl())
                .defaultHeader("Authorization", "Bearer " + props.getApiKey())
                .defaultHeader("Duffel-Version", "v2")
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * Search one-way flights for a single departure date.
     * Duffel requires one request per date — the caller loops over the range.
     */
    public List<FlightOffer> searchFlights(String origin, String destination, LocalDate date) {
        Map<String, Object> requestBody = Map.of(
                "data", Map.of(
                        "slices", List.of(Map.of(
                                "origin", origin,
                                "destination", destination,
                                "departure_date", date.toString()
                        )),
                        "passengers", List.of(Map.of("type", "adult")),
                        "cabin_class", "economy"
                )
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> response;
        try {
            response = restClient.post()
                    .uri("/air/offer_requests?return_offers=true")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(Map.class);
        } catch (RestClientException e) {
            throw new FlightApiException("Duffel API call failed: " + e.getMessage(), e);
        }

        if (response == null || !response.containsKey("data")) {
            return List.of();
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.get("data");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> offers = (List<Map<String, Object>>) data.get("offers");

        if (offers == null) return List.of();

        List<FlightOffer> result = new ArrayList<>();
        for (Map<String, Object> offer : offers) {
            try {
                result.add(mapOffer(offer));
            } catch (Exception ignored) {
                // skip malformed offers
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private FlightOffer mapOffer(Map<String, Object> offer) {
        String totalAmount = offer.get("total_amount").toString();
        String currency = offer.get("total_currency").toString();

        List<Map<String, Object>> slices = (List<Map<String, Object>>) offer.get("slices");
        List<Map<String, Object>> segments = (List<Map<String, Object>>) slices.get(0).get("segments");

        Map<String, Object> firstSegment = segments.get(0);
        Map<String, Object> lastSegment = segments.get(segments.size() - 1);

        String depAt = firstSegment.get("departing_at").toString();  // "2025-06-01T08:30:00"
        String arrAt = lastSegment.get("arriving_at").toString();

        String departureDate = depAt.substring(0, 10);
        String departureTime = depAt.length() >= 16 ? depAt.substring(11, 16) : "";
        String arrivalTime = arrAt.length() >= 16 ? arrAt.substring(11, 16) : "";

        Map<String, Object> carrier = (Map<String, Object>) firstSegment.get("marketing_carrier");
        String airlineCode = carrier.get("iata_code").toString();
        String flightNo = airlineCode + firstSegment.get("marketing_carrier_flight_number").toString();

        return new FlightOffer(departureDate, departureTime, arrivalTime,
                airlineCode, flightNo, new BigDecimal(totalAmount), currency, null);
    }
}

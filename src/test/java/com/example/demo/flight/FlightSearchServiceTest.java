package com.example.demo.flight;

import com.example.demo.flight.client.DuffelApiClient;
import com.example.demo.flight.model.FlightOffer;
import com.example.demo.flight.service.FlightSearchService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FlightSearchServiceTest {

    @Mock
    DuffelApiClient duffelApiClient;

    @InjectMocks
    FlightSearchService service;

    @Test
    void defaultDateRange_callsForTodayThroughTodayPlus14() {
        when(duffelApiClient.searchFlights(any(), any(), any())).thenReturn(List.of());

        LocalDate today = LocalDate.now();
        service.search("AMS", "KEF", null, null);

        verify(duffelApiClient).searchFlights("AMS", "KEF", today);
        verify(duffelApiClient).searchFlights("AMS", "KEF", today.plusDays(14));
    }

    @Test
    void explicitDateRange_callsOncePerDay() {
        when(duffelApiClient.searchFlights(any(), any(), any())).thenReturn(List.of());

        LocalDate start = LocalDate.of(2025, 6, 1);
        LocalDate end = LocalDate.of(2025, 6, 3);
        service.search("AMS", "KEF", start, end);

        verify(duffelApiClient).searchFlights("AMS", "KEF", LocalDate.of(2025, 6, 1));
        verify(duffelApiClient).searchFlights("AMS", "KEF", LocalDate.of(2025, 6, 2));
        verify(duffelApiClient).searchFlights("AMS", "KEF", LocalDate.of(2025, 6, 3));
    }

    @Test
    void results_sortedByPriceAscending() {
        FlightOffer expensive = offer("2025-06-01", "350.00");
        FlightOffer cheap = offer("2025-06-01", "99.50");
        FlightOffer mid = offer("2025-06-02", "199.00");

        when(duffelApiClient.searchFlights(eq("AMS"), eq("KEF"), eq(LocalDate.of(2025, 6, 1))))
                .thenReturn(List.of(expensive, cheap));
        when(duffelApiClient.searchFlights(eq("AMS"), eq("KEF"), eq(LocalDate.of(2025, 6, 2))))
                .thenReturn(List.of(mid));

        List<FlightOffer> result = service.search("AMS", "KEF",
                LocalDate.of(2025, 6, 1), LocalDate.of(2025, 6, 2));

        assertThat(result).hasSize(3);
        assertThat(result.get(0).price()).isEqualByComparingTo("99.50");
        assertThat(result.get(1).price()).isEqualByComparingTo("199.00");
        assertThat(result.get(2).price()).isEqualByComparingTo("350.00");
    }

    @Test
    void noFlights_returnsEmptyList() {
        when(duffelApiClient.searchFlights(any(), any(), any())).thenReturn(List.of());

        List<FlightOffer> result = service.search("AMS", "KEF",
                LocalDate.of(2025, 6, 1), LocalDate.of(2025, 6, 1));

        assertThat(result).isEmpty();
    }

    private FlightOffer offer(String date, String price) {
        return new FlightOffer(date, "10:00", "12:30", "FI", "FI401",
                new BigDecimal(price), "EUR", null);
    }
}

package com.example.demo.flight;

import com.example.demo.flight.client.DuffelApiClient;
import com.example.demo.flight.client.FlightApiException;
import com.example.demo.flight.model.FlightOffer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class FlightSearchControllerIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    DuffelApiClient duffelApiClient;

    @Test
    void validRequest_returns200WithFlights() throws Exception {
        FlightOffer offer = new FlightOffer("2025-06-01", "10:00", "12:30",
                "FI", "FI401", new BigDecimal("149.99"), "EUR", null);

        when(duffelApiClient.searchFlights(eq("AMS"), eq("KEF"), any(LocalDate.class)))
                .thenReturn(List.of(offer));

        mockMvc.perform(get("/flights/search")
                        .param("origin", "AMS")
                        .param("destination", "KEF")
                        .param("from", "2025-06-01")
                        .param("to", "2025-06-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].flightNumber").value("FI401"))
                .andExpect(jsonPath("$[0].price").value(149.99));
    }

    @Test
    void invalidOrigin_returns400() throws Exception {
        mockMvc.perform(get("/flights/search")
                        .param("origin", "invalid")
                        .param("destination", "KEF"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_request"));
    }

    @Test
    void invalidDestination_returns400() throws Exception {
        mockMvc.perform(get("/flights/search")
                        .param("origin", "AMS")
                        .param("destination", "kef"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_request"));
    }

    @Test
    void noFlightsFound_returns200WithEmptyList() throws Exception {
        when(duffelApiClient.searchFlights(any(), any(), any())).thenReturn(List.of());

        mockMvc.perform(get("/flights/search")
                        .param("origin", "AMS")
                        .param("destination", "KEF")
                        .param("from", "2025-06-01")
                        .param("to", "2025-06-01"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void duffelApiError_returns502() throws Exception {
        when(duffelApiClient.searchFlights(any(), any(), any()))
                .thenThrow(new FlightApiException("Connection refused"));

        mockMvc.perform(get("/flights/search")
                        .param("origin", "AMS")
                        .param("destination", "KEF")
                        .param("from", "2025-06-01")
                        .param("to", "2025-06-01"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("external_api_error"));
    }
}

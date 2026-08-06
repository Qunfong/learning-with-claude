package com.example.demo.flight.client;

public class FlightApiException extends RuntimeException {

    public FlightApiException(String message) {
        super(message);
    }

    public FlightApiException(String message, Throwable cause) {
        super(message, cause);
    }
}

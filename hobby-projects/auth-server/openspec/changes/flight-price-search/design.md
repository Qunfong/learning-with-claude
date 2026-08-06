## Context

Het project is een bestaande Spring Boot applicatie met OAuth 2.0 authenticatie. Er is nog geen vluchtzoekfunctionaliteit. We voegen een nieuwe feature toe die een externe flight data API aanroept en de goedkoopste vluchten retourneert voor een configureerbare route en datum range. De externe API (Amadeus) is gratis beschikbaar via een developer account.

## Goals / Non-Goals

**Goals:**
- Werkend REST endpoint dat vluchten ophaalt voor een opgegeven route en datum range
- Resultaten gesorteerd op prijs (goedkoopste eerst)
- Configureerbare parameters: vertrek, bestemming, datum range (default = komende 2 weken)
- Amadeus Flight Offers Search API als externe databron
- API credentials via `application.properties` / environment variables

**Non-Goals:**
- Caching van vluchtprijzen
- Boekingsfunctionaliteit
- Authenticatie vereisten op het search endpoint (voorlopig publiek)
- Ondersteuning voor meerdere stops / overstappen
- Prijshistorie of alerts

## Decisions

### 1. Externe API: Amadeus vs Skyscanner RapidAPI

**Keuze: Amadeus Flight Offers Search API**

Reden: Amadeus heeft een officiële gratis test-omgeving (`test.api.amadeus.com`) zonder credit card, met een SDK beschikbaar. Skyscanner vereist RapidAPI-account met betaalgegevens. Voor een leeromgeving is Amadeus toegankelijker.

Alternatief: RapidAPI Skyscanner — rijker UI-data, maar complexer authenticatie.

### 2. HTTP Client: RestClient (Spring 6.1+)

**Keuze: Spring `RestClient`**

Reden: Het project gebruikt Spring Boot 3.x. `RestClient` is de moderne synchrone HTTP client, eenvoudiger dan `WebClient` voor niet-reactieve code. Geen extra dependency nodig.

Alternatief: `WebClient` — beter voor reactief/async, maar overkill hier.

### 3. Authenticatie Amadeus: OAuth 2.0 Client Credentials

Amadeus gebruikt zelf ook OAuth 2.0 (`/v1/security/oauth2/token` met `client_credentials` grant). De `FlightApiService` haalt een access token op en cached dit tot vlak voor expiry.

### 4. Architectuur

```
FlightSearchController  (REST endpoint)
    └── FlightSearchService  (business logic: datum range, sorting)
            └── AmadeusApiClient  (HTTP calls + token management)
```

Geen aparte repository layer — vluchtdata wordt niet opgeslagen.

## Risks / Trade-offs

- **Rate limiting Amadeus test API** → Mitigation: test API heeft 2000 calls/maand gratis; ruim voldoende voor development
- **Amadeus test data is niet altijd realistisch** → Mitigation: acceptabel voor een leeromgeving; voor productie switch naar live API met zelfde code
- **Token expiry edge case** → Mitigation: token wordt ge-refreshed als het binnen 30 seconden verloopt
- **Luchthavencodes vereist (IATA)** → Mitigation: documenteer dat input IATA codes zijn (bijv. `AMS`, `KEF`); geen vrije tekst search

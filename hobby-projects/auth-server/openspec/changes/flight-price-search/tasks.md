## 1. Dependencies en configuratie

- [x] 1.1 Voeg Amadeus Java SDK (of geen SDK, gebruik RestClient) dependency toe aan `pom.xml`
- [x] 1.2 Voeg `amadeus.client-id` en `amadeus.client-secret` properties toe aan `application.properties`
- [x] 1.3 Maak `AmadeusProperties` configuratieklasse met `@ConfigurationProperties`

## 2. Amadeus API client

- [x] 2.1 Maak `AmadeusApiClient` klasse die token management en HTTP calls verzorgt
- [x] 2.2 Implementeer `fetchToken()` methode die POST `/v1/security/oauth2/token` aanroept met client credentials
- [x] 2.3 Implementeer token caching: sla token op met expiry timestamp, vernieuw wanneer binnen 30 seconden verlopen
- [x] 2.4 Implementeer `searchFlights(origin, destination, date)` methode die Amadeus Flight Offers Search aanroept
- [x] 2.5 Behandel Amadeus API fouten (HTTP 4xx/5xx) met een duidelijke foutmelding

## 3. Data model en service

- [x] 3.1 Maak `FlightOffer` record/DTO met velden: `departureDate`, `departureTime`, `arrivalTime`, `airline`, `flightNumber`, `price`, `currency`, `bookingUrl`
- [x] 3.2 Maak `FlightSearchService` die voor elke dag in de datum range `AmadeusApiClient.searchFlights()` aanroept
- [x] 3.3 Implementeer samenvoegen en sorteren van resultaten op prijs (goedkoopste eerst)
- [x] 3.4 Implementeer default datum range logica: `from` = vandaag, `to` = vandaag + 14 dagen

## 4. REST controller

- [x] 4.1 Maak `FlightSearchController` met `GET /flights/search` endpoint
- [x] 4.2 Valideer `origin` en `destination` parameters (niet leeg, geldig IATA formaat)
- [x] 4.3 Geef HTTP 400 terug bij ongeldige input, HTTP 502 bij externe API fout
- [x] 4.4 Geef HTTP 200 met lege lijst terug als er geen vluchten gevonden zijn

## 5. Testen

- [x] 5.1 Schrijf unit test voor `FlightSearchService` datum range logica
- [x] 5.2 Schrijf unit test voor sortering op prijs
- [x] 5.3 Schrijf integratie test voor `GET /flights/search` met gemockte `AmadeusApiClient`
- [ ] 5.4 Test het endpoint handmatig met echte Amadeus test-API credentials

## Why

Er is geen manier om snel en programmatisch goedkope vluchten op te zoeken voor een bepaalde periode. Door een vluchtzoekfunctie te bouwen kunnen gebruikers (of geautomatiseerde processen) eenvoudig de goedkoopste vluchten ophalen voor een flexibele datum range en variabele routes.

## What Changes

- Nieuwe REST endpoint `GET /flights/search` dat vluchten ophaalt via een externe flight data API
- Resultaten worden gefilterd en gesorteerd op prijs (goedkoopste eerst)
- Datum range is configureerbaar (standaard: de komende 2 weken vanaf vandaag)
- Vertrek- en bestemmingsluchthaven zijn variabel (standaard: AMS → KEF voor de demo)
- Response bevat per vlucht: datum, vluchtnummer, prijs, luchtvaartmaatschappij en boekingslink

## Capabilities

### New Capabilities

- `flight-search`: Zoeken naar vluchten op route en datum range, resultaten ophalen via externe API en teruggeven gesorteerd op prijs

### Modified Capabilities

## Impact

- Nieuwe Spring Boot controller + service in het bestaande project
- Nieuwe dependency: HTTP client (bijv. `RestClient` of `WebClient`) voor externe API calls
- Externe API: Skyscanner Rapid API of Amadeus Flight Offers API (configureerbaar via `application.properties`)
- Geen wijzigingen aan bestaande auth/OAuth code

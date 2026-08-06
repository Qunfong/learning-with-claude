## ADDED Requirements

### Requirement: Zoeken naar vluchten op route en datum range
Het systeem SHALL een REST endpoint bieden op `GET /flights/search` waarmee de gebruiker vluchten kan opzoeken voor een opgegeven vertrek- en bestemmingsluchthaven en een datum range. Resultaten worden teruggegeven gesorteerd op prijs (goedkoopste eerst).

Parameters:
- `origin` (required): IATA code van vertrekluchthaven (bijv. `AMS`)
- `destination` (required): IATA code van bestemmingsluchthaven (bijv. `KEF`)
- `from` (optional): startdatum in `yyyy-MM-dd` formaat; default = vandaag
- `to` (optional): einddatum in `yyyy-MM-dd` formaat; default = vandaag + 14 dagen

#### Scenario: Standaard zoekopdracht AMS naar KEF komende 2 weken
- **WHEN** de gebruiker `GET /flights/search?origin=AMS&destination=KEF` aanroept zonder datumparameters
- **THEN** retourneert het systeem HTTP 200 met een gesorteerde lijst van vluchten voor de komende 14 dagen

#### Scenario: Zoekopdracht met expliciete datum range
- **WHEN** de gebruiker `GET /flights/search?origin=AMS&destination=KEF&from=2025-06-01&to=2025-06-14` aanroept
- **THEN** retourneert het systeem HTTP 200 met vluchten alleen binnen die datum range

#### Scenario: Ongeldige IATA code
- **WHEN** de gebruiker een onbekende of lege `origin` of `destination` opgeeft
- **THEN** retourneert het systeem HTTP 400 met een foutmelding

### Requirement: Vluchtresultaten gesorteerd op prijs
Het systeem SHALL vluchtresultaten altijd teruggeven gesorteerd op totaalprijs oplopend (goedkoopste vlucht eerst).

Elk vluchtobject in de response SHALL minimaal bevatten:
- `departureDate`: vertrekdatum
- `departureTime`: vertrektijd
- `arrivalTime`: aankomsttijd
- `airline`: naam of code van de luchtvaartmaatschappij
- `flightNumber`: vluchtnummer
- `price`: totaalprijs in EUR
- `currency`: valuta (bijv. `EUR`)
- `bookingUrl`: optionele link naar Amadeus of boekingspagina (mag null zijn)

#### Scenario: Meerdere vluchten gevonden
- **WHEN** de Amadeus API meerdere vluchten retourneert voor de gevraagde route en periode
- **THEN** staan de vluchten in de response gesorteerd van laagste naar hoogste prijs

#### Scenario: Geen vluchten gevonden
- **WHEN** de Amadeus API geen vluchten retourneert voor de gevraagde route en periode
- **THEN** retourneert het systeem HTTP 200 met een lege lijst `[]`

### Requirement: Amadeus API authenticatie via client credentials
Het systeem SHALL zich authenticeren bij de Amadeus API via OAuth 2.0 client credentials flow. Het access token SHALL gecached worden en automatisch vernieuwd worden wanneer het verloopt of binnen 30 seconden verloopt.

Credentials (client ID en secret) SHALL instelbaar zijn via environment variables of `application.properties`.

#### Scenario: Token ophalen bij eerste API aanroep
- **WHEN** er nog geen geldig Amadeus access token gecached is en een zoekopdracht binnenkomt
- **THEN** haalt het systeem een nieuw token op via `POST /v1/security/oauth2/token` en slaat dit op

#### Scenario: Token vernieuwen bij expiry
- **WHEN** het gecachede token verlopen is of binnen 30 seconden verloopt
- **THEN** haalt het systeem automatisch een nieuw token op voordat de Amadeus search API wordt aangeroepen

#### Scenario: Amadeus API onbereikbaar
- **WHEN** de Amadeus API een fout retourneert of niet bereikbaar is
- **THEN** retourneert het systeem HTTP 502 met een foutmelding `"external_api_error"`

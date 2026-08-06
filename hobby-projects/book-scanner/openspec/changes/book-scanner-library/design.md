## Context

Er bestaat geen applicatie in deze codebase. We bouwen een greenfield full-stack applicatie waarmee gebruikers boeken kunnen scannen via hun camera of een foto-upload. De afbeelding wordt asynchroon verwerkt via een streaming pipeline (Kafka), waarna de herkende boekdata wordt opgeslagen in een persoonlijke bibliotheek. De hele stack draait lokaal via Docker Compose.

## Goals / Non-Goals

**Goals:**
- Gebruiker kan foto maken of uploaden van een boek
- Afbeelding wordt asynchroon verwerkt via een message broker (Kafka)
- Image recognition extraheert titel, auteur en/of ISBN
- Herkend boek wordt opgeslagen in persoonlijke bibliotheek
- Gebruiker kan bibliotheek bekijken en boeken verwijderen
- Alles draait via `docker-compose up`

**Non-Goals:**
- Gebruikersauthenticatie / login (single-user voor nu)
- Betaalde boekdata API's (we gebruiken Open Library of Google Books gratis tier)
- Mobiele native app
- Productie-deployment, scaling, of HA

## Decisions

### 1. Backend: Java Spring Boot
**Keuze**: Spring Boot 4.0.0 met Java 21 (Spring Framework 7.0.1, Hibernate 7.1, Kafka 4.1, Jackson 3.0)  
**Reden**: Aansluitend op de Java-leerdoelstelling van de gebruiker. Spring Boot heeft uitstekende Kafka-integratie via Spring Kafka.  
**Alternatief**: Quarkus (lichter maar minder bekend voor de doelgroep)

### 2. Frontend: Angular 21
**Keuze**: Angular 21 
**Reden**: Breed gebruikt, simpele camera/file-upload API, snel op te zetten.  
**Alternatief**: React 18 + vite 

### 3. Streaming: Apache Kafka (via Confluent Platform image)
**Keuze**: Kafka als message broker voor de image processing pipeline  
**Reden**: Leerdoelstelling omvat streaming; Kafka is industrie-standaard. Een afbeelding-upload produceert een event op topic `image.submitted`, de processor consumeert dit en publiceert het resultaat op `book.recognized`.  
**Alternatief**: RabbitMQ (eenvoudiger, maar minder leerzaam voor streaming patterns); in-memory queue (geen resiliency)

### 4. Image Recognition: Google Cloud Vision API
**Keuze**: Google Vision API voor OCR (text detection op boekomslag)  
**Reden**: Hoge accuraatheid voor tekst op covers; gratis tier voldoende voor dev/test.  
**Alternatief**: Tesseract (open-source, maar lagere kwaliteit); AWS Rekognition

### 5. Boekmeta ophalen: Open Library API
**Keuze**: Na OCR wordt de herkende tekst (titel/auteur/ISBN) opgezocht via de Open Library REST API  
**Reden**: Volledig gratis en open, geen API-key nodig.  
**Alternatief**: Google Books API (beter maar quota-beperkt)

### 6. Database: PostgreSQL
**Keuze**: PostgreSQL via officiële Docker image  
**Reden**: Simpel, bewezen, goed ondersteund door Spring Data JPA. Eén tabel voor boeken.  
**Alternatief**: SQLite (nog simpeler maar geen goede Docker-integratie met Spring)

### 7. Communicatie Frontend → Backend
**Keuze**: REST API voor upload en bibliotheekbeheer; Server-Sent Events (SSE) voor realtime feedback terwijl Kafka verwerkt  
**Reden**: SSE is eenvoudiger dan WebSockets voor unidirectionele updates (backend → frontend).

## Risks / Trade-offs

- **[Risk] Kafka complexiteit voor beginners** → Mitigatie: Gebruik Confluent Platform Docker image met Kafka UI voor visualisatie; uitgebreid commentaar in code
- **[Risk] Google Vision API vereist creditcard voor gratis tier** → Mitigatie: Documenteer alternatief met Tesseract als fallback; mock-mode voor tests
- **[Risk] OCR werkt slecht bij slechte foto's** → Mitigatie: Gebruiker krijgt foutmelding en kan opnieuw proberen; geen automatische retry
- **[Risk] Kafka is overkill voor single-user app** → Trade-off: Bewuste keuze voor leerdoelstelling; acceptabel voor deze context

## Migration Plan

Greenfield project — geen migratie nodig. Deployment:
1. `docker-compose up --build`
2. Backend start, wacht op Kafka en PostgreSQL (health checks)
3. Frontend bereikbaar op `http://localhost:3000`
4. Backend API op `http://localhost:8080`
5. Kafka UI op `http://localhost:8090`

## Open Questions

- Welke Google Vision API key configuratie? → Documenteren in `.env.example`
- Wil de gebruiker een ISBN barcode scanner als alternatief voor OCR? → Kan het boek of ISBN of alleen de titel dan krijg je aanebevelingen welke het beste matchen top 3
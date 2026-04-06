## 1. Project Setup & Docker Compose

- [x] 1.1 Initialiseer de project folder structuur: `frontend/`, `backend/`, `docker-compose.yml`
- [x] 1.2 Maak `docker-compose.yml` aan met services: postgres, zookeeper, kafka, kafka-ui, backend, frontend
- [x] 1.3 Voeg health checks toe aan docker-compose voor postgres en kafka zodat backend pas start als ze gereed zijn
- [x] 1.4 Maak `.env.example` aan met configuratievariabelen (DB credentials, Google Vision API key, Kafka bootstrap server)

## 2. Backend: Spring Boot Project Setup

- [x] 2.1 Maak Spring Boot 3 project aan (Java 21) met dependencies: Spring Web, Spring Data JPA, Spring Kafka, PostgreSQL driver, Lombok
- [x] 2.2 Configureer `application.yml` met database, Kafka en server instellingen (via environment variabelen)
- [x] 2.3 Maak `Dockerfile` voor de backend (multi-stage build: Maven build + runtime image)

## 3. Backend: Database & Domain Model

- [x] 3.1 Maak `Book` JPA entity aan met velden: id, title, author, isbn, coverUrl, description, addedAt
- [x] 3.2 Maak `BookRepository` interface aan (Spring Data JPA)
- [x] 3.3 Configureer Flyway of schema auto-create voor de `books` tabel

## 4. Backend: Image Upload API

- [x] 4.1 Maak `ImageUploadController` aan met `POST /api/images/upload` endpoint (accepteert multipart/form-data)
- [x] 4.2 Valideer bestandsgrootte (max 10MB) en bestandstype (JPEG/PNG) — return 413 / 415 bij overtreding
- [x] 4.3 Maak `ImageUploadService` aan die het geüploade bestand tijdelijk opslaat en een event publiceert naar Kafka topic `image.submitted`
- [x] 4.4 Maak Kafka producer configuratie aan (`KafkaProducerConfig`)

## 5. Backend: Image Processing Pipeline (Kafka Consumer)

- [x] 5.1 Maak Kafka consumer configuratie aan (`KafkaConsumerConfig`) die luistert op `image.submitted`
- [x] 5.2 Maak `ImageProcessingService` aan die het Kafka event consumeert
- [x] 5.3 Integreer Google Cloud Vision API voor OCR (text detection) op de afbeelding
- [x] 5.4 Maak `OpenLibraryClient` aan (RestTemplate of WebClient) die zoekt op titel/auteur/ISBN via Open Library API
- [x] 5.5 Publiceer resultaat (RECOGNIZED of FAILED + reden) naar Kafka topic `book.recognized`
- [x] 5.6 Maak Kafka consumer aan die luistert op `book.recognized` en het resultaat doorstuurt via SSE

## 6. Backend: SSE Endpoint & Bibliotheek API

- [x] 6.1 Maak `SseController` aan met `GET /api/sse/status/{uploadId}` endpoint dat SSE events streamt
- [x] 6.2 Koppel de `book.recognized` Kafka consumer aan de SSE emitter zodat de frontend realtime updates ontvangt
- [x] 6.3 Maak `LibraryController` aan met endpoints: `GET /api/books`, `GET /api/books/{id}`, `DELETE /api/books/{id}`
- [x] 6.4 Maak `LibraryService` aan die de `BookRepository` gebruikt voor CRUD operaties
- [x] 6.5 Maak `POST /api/books` endpoint aan om een herkend boek te bevestigen en op te slaan

## 7. Frontend: React Project Setup

- [x] 7.1 Maak Angular project aan in `frontend/` folder
- [x] 7.2 Voeg dependencies toe: axios (HTTP), Angular router (routing)
- [x] 7.3 Maak `Dockerfile` voor de frontend (Angular build + nginx serving)
- [x] 7.4 Configureer nginx om API calls te proxyen naar de backend (om CORS te vermijden)

## 8. Frontend: Book Scanning Pagina

- [x] 8.1 Maak `ScanPage` component aan met camera capture knop (gebruik `navigator.mediaDevices`) en file upload input
- [x] 8.2 Implementeer upload logica: POST naar `/api/images/upload`, sla `uploadId` op uit response
- [x] 8.3 Implementeer SSE verbinding op `/api/sse/status/{uploadId}` na upload
- [x] 8.4 Toon loading indicator tijdens processing (status: PROCESSING)
- [x] 8.5 Toon herkend boek (titel, auteur, cover) met "Save to Library" en "Dismiss" knoppen na RECOGNIZED event
- [x] 8.6 Toon foutmelding met retry mogelijkheid bij FAILED event

## 9. Frontend: Bibliotheek Pagina

- [x] 9.1 Maak `LibraryPage` component aan die `GET /api/books` aanroept en boeken weergeeft als kaarten
- [x] 9.2 Voeg leeg-staat melding toe wanneer bibliotheek leeg is
- [x] 9.3 Maak `BookDetailPage` component aan die `GET /api/books/{id}` aanroept en boekdetails toont
- [x] 9.4 Implementeer verwijder functionaliteit met bevestigingsdialoog (DELETE `/api/books/{id}`)
- [x] 9.5 Voeg routing toe: `/` → LibraryPage, `/scan` → ScanPage, `/books/:id` → BookDetailPage

## 10. Integratie testen & Docker Compose verificatie

- [x] 10.1 Test volledige flow lokaal via `docker-compose up --build`
- [x] 10.2 Verifieer dat Kafka UI bereikbaar is op `http://localhost:8090` en topics zichtbaar zijn
- [x] 10.3 Test image upload flow end-to-end: upload → Kafka event → OCR → Open Library → SSE → opslaan
- [x] 10.4 Test bibliotheekbeheer: boeken bekijken, detailpagina, verwijderen
- [x] 10.5 Test foutscenario's: grote bestanden, verkeerd bestandstype, onherkenbare foto

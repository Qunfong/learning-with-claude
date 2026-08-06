## Why

Mensen hebben moeite met het bijhouden van hun fysieke boekenlijst. Door een foto van een boek te maken kan de app automatisch het boek herkennen en opslaan in een persoonlijke bibliotheek — zonder handmatig invoeren.

## What Changes

- Nieuwe mobiel-vriendelijke web frontend waarmee gebruikers een foto van een boek kunnen maken of uploaden
- Backend service die afbeeldingen verwerkt via een streaming pipeline (image recognition)
- Opslag van herkende boeken in een persoonlijke bibliotheek per gebruiker
- Simpele database voor het opslaan van boekdata en bibliotheekoverzicht
- Volledige Docker Compose setup voor lokaal draaien en testen

## Capabilities

### New Capabilities

- `book-scanning`: Foto van boek maken of uploaden; afbeelding wordt via streaming verwerkt voor image recognition (titel, auteur, ISBN herkenning)
- `book-library`: Persoonlijke bibliotheek beheren — herkende boeken opslaan, bekijken en verwijderen
- `image-processing-pipeline`: Streaming verwerkingspipeline die binnenkomende afbeeldingen asynchroon verwerkt en resultaten terugstuurt

### Modified Capabilities

## Impact

- Nieuwe frontend applicatie (Angular)
- Nieuwe backend API (Java Spring Boot)
- Image recognition integratie (Google Vision API of vergelijkbaar, of open-source alternatief)
- Streaming oplossing (bijv. Kafka of RabbitMQ voor async image processing events)
- Database: PostgreSQL of SQLite voor eenvoud
- Docker Compose met alle services: frontend, backend, database, message broker
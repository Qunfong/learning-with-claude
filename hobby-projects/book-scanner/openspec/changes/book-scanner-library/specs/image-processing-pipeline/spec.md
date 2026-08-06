## ADDED Requirements

### Requirement: Consume image events from Kafka
The image processing service SHALL consume events from the `image.submitted` Kafka topic and process each image for book recognition.

#### Scenario: Successful event consumption
- **WHEN** an event is published to the `image.submitted` topic with a valid image payload
- **THEN** the consumer SHALL pick up the event within 5 seconds and begin processing

#### Scenario: Malformed event
- **WHEN** an event on `image.submitted` is missing required fields or is malformed
- **THEN** the consumer SHALL log an error, publish a `FAILED` event to `book.recognized`, and NOT retry

### Requirement: Extract text from image via OCR
The pipeline SHALL extract text from the book cover image using an OCR service (Google Cloud Vision API).

#### Scenario: Successful OCR
- **WHEN** the OCR service successfully detects text on the image
- **THEN** the pipeline SHALL extract all detected text blocks and pass them to the book metadata lookup step

#### Scenario: No text detected
- **WHEN** the OCR service returns no text results
- **THEN** the pipeline SHALL publish a `FAILED` event to `book.recognized` with reason `NO_TEXT_DETECTED`

#### Scenario: OCR service unavailable
- **WHEN** the OCR service returns an error or is unreachable
- **THEN** the pipeline SHALL publish a `FAILED` event to `book.recognized` with reason `OCR_SERVICE_ERROR`

### Requirement: Look up book metadata
The pipeline SHALL use detected text (title, author, or ISBN) to look up structured book metadata via the Open Library API.

#### Scenario: Book found by title or author
- **WHEN** OCR text matches a book in Open Library
- **THEN** the pipeline SHALL retrieve title, author, ISBN, cover URL, and description

#### Scenario: Book not found
- **WHEN** no match is found in Open Library for the detected text
- **THEN** the pipeline SHALL publish a `FAILED` event to `book.recognized` with reason `BOOK_NOT_FOUND`

### Requirement: Publish recognition result to Kafka
The pipeline SHALL publish the recognition result to the `book.recognized` Kafka topic.

#### Scenario: Successful recognition published
- **WHEN** book metadata is successfully retrieved
- **THEN** the pipeline SHALL publish an event to `book.recognized` with status `RECOGNIZED` and the full book metadata

#### Scenario: Failed recognition published
- **WHEN** any step in the pipeline fails
- **THEN** the pipeline SHALL publish an event to `book.recognized` with status `FAILED` and a reason code

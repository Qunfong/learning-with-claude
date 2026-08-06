## ADDED Requirements

### Requirement: Upload book image
The system SHALL allow a user to upload an image file (JPEG, PNG) or capture a photo via the browser camera to initiate book recognition.

#### Scenario: Successful image upload via file picker
- **WHEN** the user selects a valid image file (JPEG or PNG, max 10MB) via the file picker
- **THEN** the system SHALL accept the upload, return HTTP 202 Accepted, and emit an event to the `image.submitted` Kafka topic

#### Scenario: Successful image capture via camera
- **WHEN** the user captures a photo using the browser's camera API
- **THEN** the system SHALL accept the image, return HTTP 202 Accepted, and emit an event to the `image.submitted` Kafka topic

#### Scenario: File too large
- **WHEN** the user uploads a file larger than 10MB
- **THEN** the system SHALL reject the upload with HTTP 413 and display an error message to the user

#### Scenario: Unsupported file type
- **WHEN** the user uploads a file that is not JPEG or PNG
- **THEN** the system SHALL reject the upload with HTTP 415 and display an error message

### Requirement: Real-time processing feedback
The system SHALL provide real-time feedback to the user while the image is being processed via Server-Sent Events (SSE).

#### Scenario: Processing status update
- **WHEN** the backend processes an image event from Kafka
- **THEN** the frontend SHALL receive SSE events with status updates: `PROCESSING`, `RECOGNIZED`, or `FAILED`

#### Scenario: Recognition success feedback
- **WHEN** a book is successfully recognized
- **THEN** the frontend SHALL display the recognized book details (title, author, cover) before saving

#### Scenario: Recognition failure feedback
- **WHEN** image recognition fails or no book data can be found
- **THEN** the frontend SHALL display an error message and allow the user to retry

### Requirement: Confirm and save recognized book
The system SHALL allow the user to confirm and save a recognized book to their library.

#### Scenario: User confirms save
- **WHEN** the user clicks "Save to Library" after a successful recognition
- **THEN** the system SHALL persist the book to the database and show a success confirmation

#### Scenario: User dismisses result
- **WHEN** the user clicks "Dismiss" after recognition
- **THEN** the system SHALL discard the result without saving

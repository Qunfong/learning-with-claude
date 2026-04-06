## ADDED Requirements

### Requirement: View personal library
The system SHALL display all books that have been saved by the user in a library overview.

#### Scenario: Library with books
- **WHEN** the user navigates to the library page
- **THEN** the system SHALL display all saved books with their title, author, and cover image (if available)

#### Scenario: Empty library
- **WHEN** the user navigates to the library page and no books have been saved
- **THEN** the system SHALL display an empty state message prompting the user to scan their first book

### Requirement: Remove book from library
The system SHALL allow the user to remove a book from their personal library.

#### Scenario: Successful removal
- **WHEN** the user clicks "Remove" on a book and confirms the action
- **THEN** the system SHALL delete the book from the database and remove it from the library view

#### Scenario: Cancel removal
- **WHEN** the user clicks "Remove" but cancels the confirmation
- **THEN** the system SHALL NOT delete the book and return to the library view unchanged

### Requirement: View book details
The system SHALL allow the user to view the details of a saved book.

#### Scenario: View details of a saved book
- **WHEN** the user clicks on a book in the library
- **THEN** the system SHALL display a detail view with title, author, ISBN (if available), description (if available), and the date it was added

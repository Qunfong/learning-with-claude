## ADDED Requirements

### Requirement: Authorization endpoint accepts valid requests
The system SHALL expose a `GET /oauth/authorize` endpoint that accepts `response_type`, `client_id`, `redirect_uri`, `code_challenge`, `code_challenge_method`, and optional `state` parameters.

#### Scenario: Valid authorization request redirects to login
- **WHEN** a client sends a valid `GET /oauth/authorize` request with `response_type=code`, a registered `client_id`, a matching `redirect_uri`, and a valid `code_challenge`
- **THEN** the system redirects the user to the login/consent page with the request parameters preserved in session state

#### Scenario: Missing code_challenge is rejected
- **WHEN** a client sends `GET /oauth/authorize` without a `code_challenge` parameter
- **THEN** the system returns an error redirect to `redirect_uri` with `error=invalid_request` and `error_description` indicating PKCE is required

#### Scenario: Unregistered client_id is rejected
- **WHEN** a client sends `GET /oauth/authorize` with a `client_id` that does not exist in the client registry
- **THEN** the system returns HTTP 400 with `error=invalid_client` (no redirect, since the redirect_uri cannot be trusted)

### Requirement: Authorization code is issued after user consent
The system SHALL issue a single-use authorization code and redirect to the client's `redirect_uri` after the authenticated user grants consent.

#### Scenario: Successful authorization code issuance
- **WHEN** an authenticated user grants consent for a valid authorization request
- **THEN** the system generates a cryptographically random authorization code, persists it with the associated `client_id`, `redirect_uri`, `code_challenge`, and expiry (10 minutes), and redirects to `redirect_uri?code=<code>&state=<state>`

#### Scenario: User denies consent
- **WHEN** an authenticated user denies the authorization request
- **THEN** the system redirects to `redirect_uri?error=access_denied&state=<state>` without issuing a code

### Requirement: Token endpoint exchanges authorization code for tokens
The system SHALL expose a `POST /oauth/token` endpoint that accepts `grant_type=authorization_code`, `code`, `redirect_uri`, `client_id`, and `code_verifier`.

#### Scenario: Valid code exchange returns access and refresh tokens
- **WHEN** a client sends a valid token request with a non-expired, unused authorization code and a `code_verifier` that matches the stored `code_challenge`
- **THEN** the system marks the code as used, issues a JWT access token (TTL: 15 minutes) and a refresh token (TTL: 30 days), and returns them in a JSON response with `token_type=Bearer`

#### Scenario: Invalid code_verifier is rejected
- **WHEN** a client sends a token request with a `code_verifier` that does not satisfy `BASE64URL(SHA256(code_verifier)) == code_challenge`
- **THEN** the system returns HTTP 400 with `error=invalid_grant`

#### Scenario: Replayed authorization code is rejected
- **WHEN** a client attempts to exchange an authorization code that has already been used
- **THEN** the system returns HTTP 400 with `error=invalid_grant` and invalidates any tokens issued from that code
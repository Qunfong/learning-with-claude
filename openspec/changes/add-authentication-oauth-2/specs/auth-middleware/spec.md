## ADDED Requirements

### Requirement: Protected routes require a valid Bearer token
The system SHALL reject requests to protected API routes that do not include a valid, non-expired `Authorization: Bearer <token>` header.

#### Scenario: Request with valid token is allowed through
- **WHEN** a request includes `Authorization: Bearer <token>` and the JWT signature is valid and `exp` has not elapsed
- **THEN** the middleware passes the request to the route handler with the token claims available in the request context

#### Scenario: Request without Authorization header is rejected
- **WHEN** a request to a protected route omits the `Authorization` header
- **THEN** the middleware returns HTTP 401 with `WWW-Authenticate: Bearer` and `error=missing_token`

#### Scenario: Request with expired token is rejected
- **WHEN** a request includes a Bearer token whose `exp` claim is in the past
- **THEN** the middleware returns HTTP 401 with `error=token_expired`

#### Scenario: Request with invalid token signature is rejected
- **WHEN** a request includes a Bearer token that fails RS256 signature verification
- **THEN** the middleware returns HTTP 401 with `error=invalid_token`

### Requirement: Public routes are exempted from authentication
The system SHALL allow a configurable list of routes (e.g., health check, JWKS endpoint, login, token endpoints) to be excluded from bearer token enforcement.

#### Scenario: Health check route is accessible without a token
- **WHEN** a request is sent to `GET /health` (or any configured public route) without an `Authorization` header
- **THEN** the middleware passes the request through without authentication checks

#### Scenario: OAuth endpoints are exempt from bearer token enforcement
- **WHEN** a request is sent to `/oauth/authorize`, `/oauth/token`, `/oauth/revoke`, or `/.well-known/jwks.json`
- **THEN** the middleware does not require a Bearer token for these endpoints

### Requirement: Authenticated user identity is available to route handlers
The system SHALL populate a request-scoped identity context with `sub`, `client_id`, and `jti` from the validated token so downstream handlers can use them without re-parsing the token.

#### Scenario: Route handler receives token claims in request context
- **WHEN** a request with a valid Bearer token passes through the middleware
- **THEN** the route handler can read `sub`, `client_id`, and `jti` from the request context without performing token validation itself
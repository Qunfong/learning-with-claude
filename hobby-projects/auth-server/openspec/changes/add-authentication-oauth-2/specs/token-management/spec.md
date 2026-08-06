## ADDED Requirements

### Requirement: Access tokens are signed JWTs with standard claims
The system SHALL issue access tokens as RS256-signed JWTs containing `sub` (user ID), `client_id`, `iat`, `exp`, and `jti` claims.

#### Scenario: Access token contains required claims
- **WHEN** the system issues an access token
- **THEN** the token is a valid JWT with `sub`, `client_id`, `iat`, `exp` (15 minutes from issuance), and a unique `jti`

#### Scenario: Access token signature can be verified with public key
- **WHEN** a resource server fetches the JWKS from `/.well-known/jwks.json` and validates an access token
- **THEN** the token's RS256 signature verifies successfully against the current public key

### Requirement: Refresh tokens extend sessions without re-authentication
The system SHALL issue opaque refresh tokens with a 30-day TTL that can be exchanged for new access tokens via `POST /oauth/token` with `grant_type=refresh_token`.

#### Scenario: Valid refresh token exchange issues new access token
- **WHEN** a client sends `POST /oauth/token` with `grant_type=refresh_token` and a valid, non-expired refresh token
- **THEN** the system issues a new access token (and optionally a new refresh token) and returns them with HTTP 200

#### Scenario: Expired refresh token is rejected
- **WHEN** a client sends a token request with a refresh token whose TTL has elapsed
- **THEN** the system returns HTTP 400 with `error=invalid_grant`

### Requirement: Token introspection endpoint reports token status
The system SHALL expose `POST /oauth/introspect` that returns active/inactive status and claims for a given token, accessible only to registered resource servers.

#### Scenario: Active token returns metadata
- **WHEN** a registered resource server posts a valid, non-expired access token to `/oauth/introspect`
- **THEN** the system returns `{"active": true, "sub": "...", "exp": ..., "client_id": "..."}` with HTTP 200

#### Scenario: Expired or unknown token returns inactive
- **WHEN** an expired, revoked, or unknown token is submitted to `/oauth/introspect`
- **THEN** the system returns `{"active": false}` with HTTP 200

### Requirement: Token revocation invalidates refresh tokens
The system SHALL expose `POST /oauth/revoke` (RFC 7009) allowing clients to revoke refresh tokens.

#### Scenario: Revoked refresh token cannot be used
- **WHEN** a client revokes a refresh token via `/oauth/revoke` and then attempts to use it
- **THEN** the system returns `error=invalid_grant` on the subsequent token request

#### Scenario: Revocation of unknown token succeeds silently
- **WHEN** a client submits an unknown or already-revoked token to `/oauth/revoke`
- **THEN** the system returns HTTP 200 without error (per RFC 7009 §2.2)
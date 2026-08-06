## 1. Project Setup & Dependencies

- [x] 1.1 Add OAuth 2.0 library dependency (e.g., `spring-security-oauth2` / `passport` / equivalent for the project stack)
- [x] 1.2 Add JWT signing library with RS256 support
- [x] 1.3 Generate RSA key pair for token signing; store private key in environment config and expose public key via JWKS endpoint config
- [x] 1.4 Add environment variables: `JWT_PRIVATE_KEY`, `JWT_PUBLIC_KEY`, `ACCESS_TOKEN_TTL`, `REFRESH_TOKEN_TTL`

## 2. Database Schema

- [x] 2.1 Create `oauth_clients` table: `id`, `client_id`, `client_secret_hash`, `name`, `type` (public/confidential), `redirect_uris` (JSON array), `active`, `created_at`
- [x] 2.2 Create `authorization_codes` table: `id`, `code`, `client_id`, `user_id`, `redirect_uri`, `code_challenge`, `code_challenge_method`, `used`, `expires_at`
- [x] 2.3 Create `refresh_tokens` table: `id`, `token_hash`, `client_id`, `user_id`, `access_token_jti`, `revoked`, `expires_at`
- [x] 2.4 Add database indexes on `authorization_codes.code` and `refresh_tokens.token_hash`
- [x] 2.5 Create a scheduled job / cron to purge expired and used authorization codes and expired refresh tokens

## 3. OAuth Client Registry

- [x] 3.1 Implement `OAuthClientRepository` with create, find-by-client-id, deactivate operations
- [x] 3.2 Implement admin endpoint `POST /admin/oauth/clients` to register a new client (returns `client_id` and plaintext `client_secret` once)
- [x] 3.3 Implement admin endpoint `DELETE /admin/oauth/clients/:clientId` to deactivate a client
- [x] 3.4 Add `redirect_uri` validation: must be absolute URI, no fragment, must match allowlist exactly on authorization requests

## 4. Authorization Code Flow

- [x] 4.1 Implement `GET /oauth/authorize` — validate request params (client_id, redirect_uri, response_type, code_challenge, code_challenge_method), store pending request in session
- [x] 4.2 Implement consent/login redirect logic — after user authenticates, render or redirect to consent screen
- [x] 4.3 Implement authorization code issuance — on consent grant, generate random code, persist to `authorization_codes`, redirect to `redirect_uri?code=<code>&state=<state>`
- [x] 4.4 Implement consent denial redirect — redirect to `redirect_uri?error=access_denied&state=<state>`

## 5. Token Endpoint

- [x] 5.1 Implement `POST /oauth/token` for `grant_type=authorization_code`: validate code, verify PKCE (`BASE64URL(SHA256(verifier)) == challenge`), mark code used, issue tokens
- [x] 5.2 Implement `POST /oauth/token` for `grant_type=refresh_token`: validate refresh token, check expiry/revocation, issue new access token
- [x] 5.3 Implement JWT access token issuance with RS256 signing and claims: `sub`, `client_id`, `iat`, `exp`, `jti`
- [x] 5.4 Implement opaque refresh token issuance: generate random token, store hash in `refresh_tokens` table
- [x] 5.5 Implement replay protection: mark authorization code as used atomically; detect double-use and invalidate derived tokens

## 6. Token Introspection & Revocation

- [x] 6.1 Implement `POST /oauth/introspect`: validate caller is a registered resource server, return active/inactive with claims
- [x] 6.2 Implement `POST /oauth/revoke` (RFC 7009): look up refresh token by hash, mark as revoked, return 200 regardless of whether token was found

## 7. JWKS Endpoint

- [x] 7.1 Implement `GET /.well-known/jwks.json` returning the public RSA key in JWK format with `kid`, `kty`, `use`, `alg`, `n`, `e` fields
- [x] 7.2 Add cache headers to JWKS response (e.g., `Cache-Control: max-age=3600`)

## 8. Auth Middleware

- [x] 8.1 Implement bearer token extraction from `Authorization: Bearer <token>` header
- [x] 8.2 Implement JWT validation: verify RS256 signature, check `exp`, extract claims
- [x] 8.3 Populate request-scoped identity context with `sub`, `client_id`, `jti`
- [x] 8.4 Return HTTP 401 with appropriate `error` field for missing, expired, and invalid tokens
- [x] 8.5 Implement public route exemption list (configurable); exempt `/oauth/**`, `/.well-known/jwks.json`, `/health`
- [x] 8.6 Apply middleware to all non-public API routes

## 9. Audit Mode & Cutover

- [x] 9.1 Add `AUTH_ENFORCE` environment variable flag (default: `false`); in audit mode log missing/invalid tokens but do not reject requests
- [ ] 9.2 Deploy with `AUTH_ENFORCE=false`, verify logs show expected token usage patterns
- [ ] 9.3 Switch to `AUTH_ENFORCE=true` after confirming all clients are sending valid tokens

## 10. Testing

- [x] 10.1 Unit tests for PKCE verification logic (valid verifier, tampered verifier, wrong method)
- [x] 10.2 Integration tests for full authorization code flow: authorize → consent → code exchange → token validation
- [x] 10.3 Integration tests for refresh token flow: issue → refresh → verify new access token
- [x] 10.4 Integration tests for token revocation and introspection
- [x] 10.5 Integration tests for auth middleware: protected route with valid token, missing token, expired token, invalid signature
- [x] 10.6 Integration tests for client registry: register, deactivate, reject unregistered client

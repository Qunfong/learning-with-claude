## Context

The application exposes API routes with no authentication. Users and third-party clients cannot be identified or authorized. We are adding OAuth 2.0 (RFC 6749) using the Authorization Code flow with PKCE (RFC 7636) — the recommended flow for web and mobile clients — to provide a secure, standards-compliant authentication layer.

## Goals / Non-Goals

**Goals:**
- Implement Authorization Code + PKCE flow for human-facing clients
- Issue short-lived access tokens (JWT or opaque) and long-lived refresh tokens
- Protect all non-public API routes with bearer token middleware
- Provide OAuth client registration so first-party and third-party apps can integrate
- Token introspection endpoint for resource servers

**Non-Goals:**
- Client Credentials flow (machine-to-machine) — deferred to a follow-up
- Social login / identity federation (Google, GitHub) — out of scope for this change
- OpenID Connect (OIDC) layer — may follow once core OAuth 2.0 is stable
- Fine-grained scope enforcement beyond "authenticated / not authenticated"

## Decisions

### Token format: JWT (signed, not encrypted)

**Decision**: Issue JWTs signed with RS256.

**Rationale**: Resource servers can validate tokens locally without a round-trip to the authorization server. RS256 allows public-key distribution so downstream services don't hold the signing secret.

**Alternative considered**: Opaque tokens with a central introspection endpoint — simpler to revoke but adds latency and a single point of failure for every request.

### Token storage: Database-backed refresh tokens, stateless access tokens

**Decision**: Persist refresh tokens in the database; access tokens are stateless JWTs.

**Rationale**: Refresh tokens need to be revocable (e.g., on logout or compromise). Access tokens are short-lived (15 minutes) so the revocation window is acceptable without a blocklist.

**Alternative considered**: Storing all tokens in Redis — adds an operational dependency; deferred unless revocation latency becomes a requirement.

### PKCE enforcement: Required for all public clients

**Decision**: Require `code_challenge` / `code_verifier` for every authorization code request.

**Rationale**: Eliminates authorization code interception attacks. RFC 9700 now recommends PKCE for all clients, not just public ones.

### Authorization server: Embedded, not external

**Decision**: Implement the authorization server within the application rather than deploying a standalone IdP (Keycloak, Auth0, etc.).

**Rationale**: Reduces operational complexity for an initial implementation. The spec-driven interface means migrating to an external IdP later requires only swapping the token issuance layer.

**Alternative considered**: Keycloak — full-featured but heavyweight; deferred until multi-tenant or enterprise requirements emerge.

## Risks / Trade-offs

- **JWT revocation gap** → Mitigation: Keep access token TTL at 15 minutes; implement a token blocklist if requirements tighten.
- **Key rotation complexity** → Mitigation: Expose a JWKS endpoint (`/.well-known/jwks.json`) so clients can fetch the current public key; rotate keys with a grace period overlap.
- **PKCE implementation bugs** → Mitigation: Use a well-tested OAuth library rather than hand-rolling the code challenge verification.
- **Database token table growth** → Mitigation: Add a background job to purge expired refresh tokens on a schedule.

## Migration Plan

1. Deploy schema migrations for `oauth_clients`, `authorization_codes`, and `tokens` tables.
2. Register first-party clients (web app, mobile app) via the client registry.
3. Deploy the application with the auth middleware in **audit mode** (log missing tokens, don't reject) for one release cycle.
4. Switch middleware to **enforce mode** and communicate the breaking change to API consumers.
5. **Rollback**: Revert middleware to audit mode; tokens and client records are non-destructive to remove.

## Open Questions

- Should we support token rotation on refresh (issue a new refresh token and invalidate the old one)? — Recommended for security but adds complexity.
- What is the desired access token TTL? (Proposed: 15 minutes; needs product sign-off.)
- Do we need a user-facing "revoke all sessions" feature at launch?
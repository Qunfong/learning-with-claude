## Why

The application currently lacks a standardized authentication mechanism, leaving user identity unverified and APIs unprotected. Adding OAuth 2.0 enables secure, delegated authorization using industry-standard flows that third-party identity providers already support.

## What Changes

- Introduce an OAuth 2.0 authorization server integration (authorization code flow with PKCE)
- Add token issuance, validation, and refresh endpoints
- Protect existing API routes with bearer token middleware
- Store and manage OAuth client registrations and token records
- **BREAKING**: API endpoints will require a valid `Authorization: Bearer <token>` header

## Capabilities

### New Capabilities
- `oauth-authorization`: Authorization code flow with PKCE — redirects, authorization grants, and callback handling
- `token-management`: Issuance, validation, introspection, and refresh of access/refresh tokens
- `oauth-client-registry`: Registration and management of OAuth 2.0 client applications
- `auth-middleware`: Bearer token enforcement middleware for protecting API routes

### Modified Capabilities
<!-- No existing spec-level requirement changes -->

## Impact

- **APIs**: All existing API routes gain a required `Authorization` header; public/health routes exempted
- **Dependencies**: Requires an OAuth 2.0 library (e.g., `spring-security-oauth2` / `passport-oauth2` depending on stack) and a token store (database or Redis)
- **Database**: New tables/collections for `oauth_clients`, `authorization_codes`, and `tokens`
- **Configuration**: New environment variables for client secrets, token signing keys, and identity provider URLs
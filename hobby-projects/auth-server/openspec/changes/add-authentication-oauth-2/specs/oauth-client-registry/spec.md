## ADDED Requirements

### Requirement: OAuth clients can be registered with required metadata
The system SHALL store OAuth client registrations with `client_id`, `client_secret` (hashed), `redirect_uris` (allowlist), `name`, and `type` (`public` or `confidential`).

#### Scenario: Registering a valid client stores its metadata
- **WHEN** an administrator registers a new OAuth client with a name, at least one redirect URI, and a client type
- **THEN** the system generates a unique `client_id`, a `client_secret` (for confidential clients), persists the registration, and returns the credentials

#### Scenario: Duplicate redirect_uri is rejected
- **WHEN** a redirect URI in a registration request is not a valid absolute URI or contains a fragment (`#`)
- **THEN** the system returns a 400 error identifying the invalid URI

### Requirement: Authorization requests validate redirect_uri against registered allowlist
The system SHALL reject any authorization request whose `redirect_uri` does not exactly match one of the client's registered redirect URIs.

#### Scenario: Exact match redirect_uri is accepted
- **WHEN** an authorization request includes a `redirect_uri` that exactly matches a registered URI for the given `client_id`
- **THEN** the system proceeds with the authorization flow

#### Scenario: Unregistered redirect_uri is rejected without redirect
- **WHEN** an authorization request includes a `redirect_uri` not in the client's allowlist
- **THEN** the system returns HTTP 400 with `error=invalid_request` and does NOT redirect (to prevent open-redirect attacks)

### Requirement: Clients can be deactivated to invalidate future requests
The system SHALL allow administrators to deactivate a client, after which all authorization and token requests from that `client_id` are rejected.

#### Scenario: Deactivated client cannot initiate new flows
- **WHEN** a deactivated client sends an authorization or token request
- **THEN** the system returns `error=invalid_client` and refuses to proceed

#### Scenario: Existing tokens from deactivated client remain valid until expiry
- **WHEN** a client is deactivated and a resource server validates a previously issued access token
- **THEN** the access token remains valid until its `exp` claim (eventual consistency via JWT statelessness)
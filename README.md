# Learning with Claude

A hands-on learning project for becoming a senior Java developer, built step by step with Claude as a mentor.

## Change History

| Date       | Change | Learnings | Spec |
|------------|--------|-----------|------|
| 2026-04-02 | OAuth 2.0 authorization: authorization code + PKCE, JWT (RS256), bearer token middleware, client registry, token introspection | OAuth 2.0 flows, PKCE, JWT vs opaque tokens, RS256, JWKS endpoint, stateless auth, Spring Security | [openspec](openspec/changes/add-authentication-oauth-2/proposal.md) |
| 2026-04-03 | Flight price search: `GET /flights/search` backed by Duffel API, cheapest-first sorting, configurable route and date range | `RestClient` (Spring 6.1+), OAuth 2.0 client credentials flow, token caching, layered architecture (Controller → Service → Client) | [openspec](openspec/changes/flight-price-search/proposal.md) |
| 2026-04-06 | Book scanner: photo-based OCR pipeline (Tesseract), Kafka event streaming, Open Library enrichment, SSE for real-time feedback, Angular frontend, Docker Compose stack | Kafka producer/consumer, event-driven architecture, SSE vs WebSockets, Spring Kafka, Docker Compose health checks | [openspec](openspec/changes/book-scanner-library/proposal.md) |
| 2026-04-06 | CI fix: migrated to Spring Boot 4 test API | Spring Boot 4 modularization, `@MockitoBean` (moved to Spring Framework), `spring-boot-starter-webmvc-test`, reading migration guides | — |

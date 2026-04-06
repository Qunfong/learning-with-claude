# Learning with Claude

A hands-on learning project for becoming a senior Java developer, built step by step with Claude as a mentor.

## Change History

| Date       | Change | Learnings | Spec |
|------------|--------|-----------|------|
| 2026-04-02 | OAuth 2.0 authorization: authorization code + PKCE, JWT (RS256), bearer token middleware, client registry, token introspection | OAuth 2.0 flows, PKCE, JWT vs opaque tokens, RS256, JWKS endpoint, stateless auth, Spring Security | [openspec](openspec/changes/add-authentication-oauth-2/proposal.md) |
| 2026-04-03 | Flight price search: `GET /flights/search` backed by Duffel API, cheapest-first sorting, configurable route and date range | `RestClient` (Spring 6.1+), OAuth 2.0 client credentials flow, token caching, layered architecture (Controller → Service → Client) | [openspec](openspec/changes/flight-price-search/proposal.md) |
| 2026-04-06 | Book scanner: photo-based OCR pipeline (Tesseract), Kafka event streaming, Open Library enrichment, SSE for real-time feedback, Angular frontend, Docker Compose stack | Kafka producer/consumer, event-driven architecture, SSE vs WebSockets, Spring Kafka, Docker Compose health checks | [openspec](openspec/changes/book-scanner-library/proposal.md) |
| 2026-04-06 | GitHub Actions CI: three jobs (auth-server, book-scanner backend, Angular frontend), artifact upload, secrets for JWT keys, Dependabot for automated dependency updates | GitHub Actions jobs/steps/triggers, Maven in CI (`--batch-mode`), `actions/setup-java` with Temurin, separating secrets from config, Dependabot | — |
| 2026-04-06 | Upgraded to Spring Boot 4 (auth-server 4.1.0-M4, book-scanner 4.0.5): configured Maven milestone repository in CI settings.xml, Java 21/25, Maven toolchains | Spring Boot 4 module split, milestone repos vs Maven Central, Maven `settings.xml` and `toolchains.xml`, CI environment config via `env:` | — |
| 2026-04-06 | CI fix: migrated to Spring Boot 4 test API (`@MockitoBean`, new `@AutoConfigureMockMvc` package, `spring-boot-starter-webmvc-test`) | Spring Boot 4 test modularization, `@MockitoBean` moved to Spring Framework, always read the migration guide on major version upgrades | — |

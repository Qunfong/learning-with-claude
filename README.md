# Learning with Claude

A hands-on Java engineering log. `hobby-projects/` are full apps built for
real — auth, persistence, CI, real infra. `learning/` is a structured
curriculum for understanding AI-assisted engineering, one runnable demo per
concept.

A richer, browsable version of this page (with a phase-by-phase rail and
live links) is published via GitHub Pages: **https://qunfong.github.io/learning-with-claude/**

## hobby-projects/

| Project | What it is | Stack |
|---|---|---|
| [auth-server](hobby-projects/auth-server/) | OAuth 2.0 authorization server — auth code + PKCE, JWT (RS256) via JWKS, client registry, token introspection & revocation. Also serves a flight-price search endpoint backed by an external flights API. | Spring Boot 4, Java 21, Spring Security, JWT/JWKS |
| [book-scanner](hobby-projects/book-scanner/) | Photo-based OCR pipeline for building a personal library. Tesseract OCR, Kafka event streaming, Open Library enrichment, SSE for live feedback, Angular frontend + Playwright e2e, full Docker Compose stack. | Spring Boot 4, Java 25, Kafka, Angular 21, Docker |

Each project keeps its own `openspec/` trail (why it was built, what
changed) — see `hobby-projects/<project>/openspec/`.

## learning/ai-learning/

Sixteen phases, each an independent, runnable Maven demo — from raw tokens
to a resilient, evaluated, memory-backed multi-agent system, closing with a
generated capstone deck (`demos/phase15-capstone/SLIDES.md`).

| # | Phase | Concept |
|---|---|---|
| 0 | [tokens-inference](learning/ai-learning/demos/phase0-tokens-inference/) | tokenization, sampling, model weights |
| 1 | [local-serving](learning/ai-learning/demos/phase1-local-serving/) | local vs hosted inference |
| 2 | [prompting-rag](learning/ai-learning/demos/phase2-prompting-rag/) | prompt structure, RAG from scratch |
| 3 | [tool-calling](learning/ai-learning/demos/phase3-tool-calling/) | structured tool invocation |
| 4 | [agents](learning/ai-learning/demos/phase4-agents/) | agent loop, memory, guardrails, retries |
| 5 | [skills](learning/ai-learning/demos/phase5-skills/) | packaging repeatable expertise |
| 6 | [mcp](learning/ai-learning/demos/phase6-mcp/) | Model Context Protocol servers |
| 7 | [multi-agent](learning/ai-learning/demos/phase7-multi-agent/) | orchestrator, coder & reviewer via A2A |
| 8 | [autonomous](learning/ai-learning/demos/phase8-autonomous/) | plan → code → review → test → PR, checkpointed |
| 9 | [agent-iam](learning/ai-learning/demos/phase9-agent-iam/) | scoped, time-bounded credentials — delegation vs impersonation |
| 10 | [frameworks-protocols](learning/ai-learning/demos/phase10-frameworks-protocols/) | real A2A agent-card discovery, x402 payment retry |
| 11 | [resilient-pipeline](learning/ai-learning/demos/phase11-resilient-pipeline/) | circuit breakers, schema-validated handoffs, confidence gates |
| 12 | [eval-harness](learning/ai-learning/demos/phase12-eval-harness/) | three-layer agent evaluation framework |
| 13 | [agent-memory-v2](learning/ai-learning/demos/phase13-agent-memory-v2/) | HNSW-lite ANN index, hybrid search, graph memory |
| 14 | [domain-specialization](learning/ai-learning/demos/phase14-domain-specialization/) | four levers of domain specialization |
| 15 | [capstone](learning/ai-learning/demos/phase15-capstone/) | generated slide deck + a combined demo chaining the curriculum |

Curriculum index: [`ai-coding-learning-plan.md`](learning/ai-learning/ai-coding-learning-plan.md).
Gap analysis behind phases 9-14: [`ai-learning-gap-review/NOTES.md`](learning/ai-learning-gap-review/NOTES.md).

## Change History

| Date       | Change | Learnings | Spec |
|------------|--------|-----------|------|
| 2026-04-02 | OAuth 2.0 authorization: authorization code + PKCE, JWT (RS256), bearer token middleware, client registry, token introspection | OAuth 2.0 flows, PKCE, JWT vs opaque tokens, RS256, JWKS endpoint, stateless auth, Spring Security | [openspec](hobby-projects/auth-server/openspec/changes/add-authentication-oauth-2/proposal.md) |
| 2026-04-03 | Flight price search: `GET /flights/search` backed by Duffel API, cheapest-first sorting, configurable route and date range | `RestClient` (Spring 6.1+), OAuth 2.0 client credentials flow, token caching, layered architecture (Controller → Service → Client) | [openspec](hobby-projects/auth-server/openspec/changes/flight-price-search/proposal.md) |
| 2026-04-06 | Book scanner: photo-based OCR pipeline (Tesseract), Kafka event streaming, Open Library enrichment, SSE for real-time feedback, Angular frontend, Docker Compose stack | Kafka producer/consumer, event-driven architecture, SSE vs WebSockets, Spring Kafka, Docker Compose health checks | [openspec](hobby-projects/book-scanner/openspec/changes/book-scanner-library/proposal.md) |
| 2026-04-06 | GitHub Actions CI: three jobs (auth-server, book-scanner backend, Angular frontend), artifact upload, secrets for JWT keys, Dependabot for automated dependency updates | GitHub Actions jobs/steps/triggers, Maven in CI (`--batch-mode`), `actions/setup-java` with Temurin, separating secrets from config, Dependabot | — |
| 2026-04-06 | Upgraded to Spring Boot 4 (auth-server 4.1.0-M4, book-scanner 4.0.5): configured Maven milestone repository in CI settings.xml, Java 21/25, Maven toolchains | Spring Boot 4 module split, milestone repos vs Maven Central, Maven `settings.xml` and `toolchains.xml`, CI environment config via `env:` | — |
| 2026-04-06 | CI fix: migrated to Spring Boot 4 test API (`@MockitoBean`, new `@AutoConfigureMockMvc` package, `spring-boot-starter-webmvc-test`) | Spring Boot 4 test modularization, `@MockitoBean` moved to Spring Framework, always read the migration guide on major version upgrades | — |
| 2026-08-06 | Repo restructure: split into `hobby-projects/` and `learning/`, each project keeping its own local `openspec/`; added `learning/ai-learning/` (phase0-8 curriculum, skills, plan) | Monorepo layout, keeping spec history local to the project it describes rather than centralized | — |
| 2026-04-06 | Book scanner: added Playwright e2e tests (library + book-detail flows), translated remaining Dutch backend log messages to English | Playwright fixtures/specs, keeping log output in the repo's working language | — |
| 2026-08-06 | Added GitHub Pages landing page: terminal-styled overview of `hobby-projects/`, `learning/`, changelog, resources | Static site via `docs/`, no build step needed for GitHub Pages | [site](https://qunfong.github.io/learning-with-claude/) |
| 2026-08-10 | Closed 6 curriculum gaps found by benchmarking phase0-8 against an external AI-agent course (agent IAM, framework/protocol standards, resilient multi-agent pipelines, eval harness, memory v2, domain specialization) as phases 9-14, renumbered from a prior phase10-15 draft, added phase15 as a capstone (generated slide deck + combined demo) | Circuit breakers vs retries, schema-validated agent handoffs with confidence-gated abstention, HNSW-lite ANN indexing, AWS's three-layer eval framework, credential delegation narrowing scope/expiry | [gap review](learning/ai-learning-gap-review/NOTES.md) |
| 2026-08-10 | Translated phase0-8 curriculum docs (and related OpenSpec specs) from Dutch to English; redesigned phase15's slide-deck diagrams as hand-laid-out SVGs instead of plain Mermaid; retired the stale, phase0-1-only top-level `slides.md` in favor of the generated, 16-phase `phase15-capstone/SLIDES.md` | Keep captured model/tool output verbatim with a gloss rather than fabricating translated output; a generated, regeneratable deck beats a hand-maintained one that drifts | — |

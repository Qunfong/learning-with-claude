# Phase 10 — Demo: Framework & Protocol Standards — A2A Agent Cards + x402 Payments (Gap 3)

Goal: close Gap 3 from `learning/ai-learning-gap-review/NOTES.md` ("No
framework or protocol-standard exposure — A2A, x402"). Every demo in
phase0-10 hand-rolls the agent loop, tool schema, and MCP transport from raw
`HttpClient`/Jackson — a deliberate choice for understanding mechanics, but
`phase7-multi-agent`'s `OrchestratorAgent` wires `A2AAgent` references
(`CoderAgent`/`ReviewerAgent`) into its constructor at **compile time**, and
that phase's own README flags the gap without closing it: "real A2A's answer
is Agent Cards served from a well-known URL." x402 (Coinbase's HTTP-based
agent-payment protocol) wasn't mentioned anywhere in the repo at all. This
phase builds both: real runtime agent-card discovery over live HTTP sockets,
and a minimal payment-required/retry flow modeled on x402's shape.

**This models the pattern behind Google's A2A agent-card discovery and
Coinbase's x402 payment protocol — it is NOT the real A2A spec or a real
x402 payment client.** There's no JSON-RPC 2.0 task/session protocol, no
agent card signing or mTLS, no streaming (SSE) or push notifications, no
blockchain wallet, no USDC transfer, no `X-PAYMENT` header, no payment
facilitator. What's here is the mechanics AWS's Module 2/4/8 content
describes at the architecture level, stripped to their essence: a
well-known-path HTTP card endpoint, a runtime capability registry built by
polling it, a trusted-origin allowlist checked before any network call, and
a typed "payment required, pause, retry" signal. If you need the real thing,
this is the mental model to bring to the actual A2A and x402 specs — not a
substitute for reading them.

This is a fully independent Maven module (own `pom.xml`, flat class
structure), same convention as every other phase — no imports from
`phase7-multi-agent/`. `A2AAgent`, `Task`, `TaskResult`, `TaskState`, and
`Artifact` are copied verbatim from `phase7-multi-agent`; `CoderAgent` and
`ReviewerAgent` are copied/adapted with their `handle()` bodies reduced to
deterministic mocks (see "Deviations" below) because this phase's
load-bearing deliverable is the discovery/transport layer, not another LLM
demo.

## Architecture

| Class | Role |
|---|---|
| `AgentCard` | record: `name`, `capabilities`, `endpoint` — the object served as JSON at the well-known path. In phase7 `endpoint` was a decorative `in-process://...` string; here it's a real `http://localhost:<port>` another process actually connects to |
| `AgentCardServer` | embedded JDK `HttpServer` (`com.sun.net.httpserver`, zero new dependency), bound to `localhost` on an OS-assigned port, serving one agent's card as hand-rolled JSON at `GET /.well-known/agent-card.json` |
| `A2AAgent` | interface (`card()` + `handle(Task)`), unchanged from phase7 — what changes is *how* an orchestrator finds an implementation |
| `CoderAgent` | capability `code.generate`; real `AgentCardServer`, mock `handle()` (no live/mocked Ollama call — see Deviations) |
| `ReviewerAgent` | capability `code.review`; real `AgentCardServer`, mock `handle()` (drops phase7's static-regex + LLM review pass) |
| `TestWriterAgent` | capability `test.write` — the "third agent," started strictly after the orchestrator's first poll, used to prove dynamic discovery |
| `RogueAgent` | capability `code.generate`; a real, correctly-functioning card server with nothing wrong with it — rejected purely because its origin is never added to the orchestrator's allowlist |
| `OrchestratorAgent` | runtime `capability -> AgentCard` registry (`ConcurrentHashMap`) built by real `HttpClient` GETs against card URLs; `discover()` never throws — malformed URL, unreachable host, non-200, and malformed JSON body are each caught, logged, and skipped; origin is checked against a trusted allowlist *before* any network call is sent |
| `Task` / `TaskResult` / `TaskState` / `Artifact` | A2A task-card shapes, copied verbatim from phase7 — unchanged in this module |
| `MeteredTool` | toy tool that throws `PaymentRequiredException` on every call until `authorizePayment()` has run once, then serves normally |
| `PaymentRequiredException` | typed signal modeling an HTTP 402 response — carries `amountDue`/`currency`, nothing else |
| `X402Pipeline` | the pause-for-payment-then-retry-once flow: call tool, catch 402, authorize, retry exactly once |
| `Phase11Demo` | runnable `main` — walks all five discovery scenarios, then the x402 mock flow |

## Running it

Run from **this** directory (`demos/phase10-frameworks-protocols/`).

```bash
mvn -q compile
mvn -q test
mvn -q compile exec:java
```

No `-D` mock flags — unlike phase7's `-Dphase7.mock=true`, this module has
no live LLM dependency at all to switch on or off. `CoderAgent`/
`ReviewerAgent`'s `handle()` bodies are always deterministic mocks (see
Deviations), and `mvn test` runs entirely against real localhost sockets
with no external service required, so the module is hermetic by default.

## What the demo actually demonstrates

`Phase11Demo` walks through five discovery scenarios plus the x402 flow,
each mapped straight to a required test:

1. **Initial discovery.** `CoderAgent` and `ReviewerAgent` each start a real
   embedded `HttpServer`; the orchestrator's allowlist starts pre-seeded
   with both origins, and `discoverAll` does a genuine HTTP GET against each
   card URL. The registry ends up with `code.generate -> CoderAgent` and
   `code.review -> ReviewerAgent`, resolved from real JSON bodies, not
   compile-time wiring.
2. **Malformed card URL.** `"http://local host:1234/..."` (a space in the
   host) is rejected at URI-parse time — logged `REJECTED malformed`, never
   thrown out of `discover()`. Proves the orchestrator survives a garbage
   input instead of crashing the poll loop.
3. **Unreachable card URL.** `http://localhost:1` is deliberately trusted
   but nothing is listening there — the `IOException` from the failed
   connection is caught and logged `UNREACHABLE`, not propagated.
4. **Untrusted origin.** `RogueAgent` is a completely real, reachable,
   well-formed card server — the *only* thing wrong is its origin was never
   added to the allowlist. `OrchestratorAgent` checks the origin **before**
   sending any HTTP request, so the rogue server never even receives a
   connection attempt; this is AWS Module 8's "agent card discovery from
   trusted sources only" defense, concretely built rather than described.
5. **Dynamic discovery.** `TestWriterAgent` is started, and its origin
   trusted, only *after* the orchestrator has already completed its first
   poll. `resolve("test.write")` is empty beforehand and present after a
   second `discover()` call — proving the registry is a live, mutable map
   with no "final wiring" step, unlike phase7's constructor injection.

**Part C — x402 mock payment-retry:** `MeteredTool.call()` throws
`PaymentRequiredException(0.01, "USD")` on the first call.
`X402Pipeline.callWithPaymentRetry` catches it, calls the mock
`authorizePayment`, and retries exactly once, returning the real result the
second time. This proves the shape of a pause-on-402/authorize/retry-once
flow, the same "pause the pipeline for an external gate" pattern
`phase9-agent-iam`'s and `phase4-agents`' `confirmHook` use for human
approval, applied here to a payment gate instead.

## Deviations from the real specs

**A2A:**
- **No JSON-RPC 2.0 task protocol.** Real A2A agents exchange tasks over
  JSON-RPC with a defined lifecycle (`submitted` / `working` /
  `input-required` / `completed` / ...) and streaming responses via SSE.
  This module's `Task`/`TaskResult`/`TaskState` are the same simplified
  shapes phase7 used — and task handoff (`A2AAgent.handle(task)`) is still
  an in-process Java method call, never actually POSTed to the discovered
  `endpoint`. The only real network traffic in this module is card
  *discovery*; task *execution* is not wired to HTTP at all.
- **No card authentication or signing.** Trust is a flat in-memory
  `Set<String>` of origin strings populated by whoever calls
  `trustedOrigins.add(...)`. There's no certificate chain, no signature over
  the card body, no mTLS — anything that can bind a socket at a trusted-
  looking origin is trusted. A real implementation needs cryptographic
  identity, not string equality.
- **No capability negotiation, versioning, or schema validation** beyond
  checking that `name`/`capabilities`/`endpoint` are present in the parsed
  JSON. No semver on cards, no compatibility checks.
- **No streaming, multi-turn conversations, or push notifications** — a
  single request/response GET is the entire transport.

**x402:**
- **No real payment rails whatsoever.** No blockchain, no USDC, no wallet,
  no Coinbase facilitator. `authorizePayment()` unconditionally flips a
  `boolean`; there is no proof-of-payment to verify.
- **No real HTTP 402.** `PaymentRequiredException` is a plain Java exception
  thrown from a method call — never an actual HTTP response with status 402
  and an `X-PAYMENT` challenge header that a client parses and resubmits
  against.
- **No idempotency, replay protection, or ledger.** Nothing stops calling
  `authorizePayment` twice, and nothing records that a specific payment was
  ever actually made beyond the tool's own `paid` flag.
- **CoderAgent/ReviewerAgent's `handle()` is deterministic mock, not a live
  LLM call.** Phase7's `CoderAgent` runs a full `AgentLoop` against Ollama
  (real or `-Dphase7.mock=true` canned); this module intentionally drops
  that to keep the deliverable focused on transport/discovery and to keep
  `mvn test` hermetic (no Ollama dependency, no network flakiness in CI).

## Tests

**`AgentCardDiscoveryTest`** (6 tests) — every server is a live
`AgentCardServer` on a real OS-assigned localhost port; `OrchestratorAgent`
makes genuine `HttpClient` GETs against them. Nothing here is mocked.

- `thirdAgentRegisteredAfterStartupIsDiscoveredOnRepoll` — dynamic
  discovery: a third agent started and trusted after the orchestrator's
  first poll is picked up on a second poll, and the original two
  capabilities remain resolvable (re-poll doesn't clobber the registry).
- `malformedUrlIsHandledGracefully` — a URL with a space in the host is
  rejected at parse time, logged `REJECTED malformed`, registry stays
  empty.
- `unreachableUrlIsHandledGracefully` — a trusted-but-dead port produces a
  caught `IOException`, logged `UNREACHABLE`, not thrown.
- `malformedJsonBodyIsHandledGracefully` — a real server that responds `200
  OK` with `{not valid json` is also caught and logged `MALFORMED`, proving
  the defense covers bad bodies from *live* servers, not just down ones.
- `untrustedOriginIsRejectedEvenWhenCardIsValid` — `RogueAgent`'s real,
  well-formed card from an origin not on the allowlist is rejected and does
  **not** overwrite the already-trusted `CoderAgent` entry for the same
  capability.
- `cardIsServedAsRealJsonOverRealHttp` — a plain `HttpClient` GET against
  `CoderAgent`'s card URL gets back `200`, `Content-Type: application/json`,
  a body containing `"name":"CoderAgent"` and `"code.generate"`, and
  explicitly asserts the endpoint is a real URL, not phase7's
  `in-process://` placeholder.

**`X402Test`** (3 tests):

- `unpaidCallThrowsPaymentRequired` — the first call on a fresh
  `MeteredTool` always throws `PaymentRequiredException(0.01, "USD")`.
- `pipelinePausesForPaymentThenRetriesSuccessfully` —
  `X402Pipeline.callWithPaymentRetry` catches the 402, authorizes, and
  returns the real paid-call result on the automatic retry.
- `secondCallAfterPaymentDoesNotNeedToPayAgain` — once
  `authorizePayment` has run, subsequent calls succeed directly with no
  further payment gate.

# Phase 11 — Demo: Resilient Multi-Agent Pipeline (Gap 4)

Goal: close Gap 4 from `learning/ai-learning-gap-review/NOTES.md`
("Production multi-agent failure handling — partially covered, shallow").
`phase7-multi-agent`/`phase8-autonomous` demonstrate real, valuable failure
modes (organic ESCALATE runs, `--chaos-fail`) but AWS Module 4's specific
vocabulary and mechanisms are absent there: no **circuit breakers** wrapping
a failing dependency (vs. phase8's simple retry-then-escalate), no
**schema-validated handoff contracts** enforced at the boundary (phase7's
`Task`/`TaskResult` are plain records with no runtime schema validation), and
no **confidence scoring / abstention** (an agent choosing not to answer
rather than guessing). This phase builds all three in hand-rolled Java.

**This models the mechanics AWS Module 4 describes — it is NOT a production
resilience stack.** There's no distributed tracing, no real
compounding-non-determinism math, no persistence of circuit-breaker state
across process restarts, and no metrics/alerting integration. What's here is
the shape: a per-dependency failure-streak breaker, a hard schema gate at
every agent-to-agent handoff, and a numeric confidence check the pipeline
actually acts on. If you need the real thing, this is the mental model to
bring to Resilience4j / Hystrix-style breakers and a real JSON Schema
validator — not a substitute for them.

This is a fully independent Maven module (own `pom.xml`, flat class
structure), same convention as every other phase — no imports from
`phase7-multi-agent/` or `phase8-autonomous/`. `Task.java`, `TaskResult.java`,
`TaskState.java`, `A2AAgent.java`, `AgentCard.java`, `Artifact.java`, and
`Retry.java` are copied in and adapted (see "Deviations" below).

## Architecture: schema-validated handoffs + confidence gate + per-hop breaker

| Class | Role |
|---|---|
| `Task` / `TaskResult` / `TaskState` / `AgentCard` / `Artifact` | A2A task-card records, copied from `phase7-multi-agent`. `TaskResult` adds `confidence` (`double`, `[0.0, 1.0]`) — the field that makes abstention possible |
| `A2AAgent` | interface every agent implements — `card()` + `handle(Task)` |
| `HandoffSchema` | hand-rolled JSON-schema check (Jackson `JsonNode` field/type inspection, no external schema library) defining `TASK_RESULT_FIELDS`: `taskId`, `state`, `artifacts`, `issues`, `message`, `confidence` — plus an enum check on `state` and a range check on `confidence` |
| `HandoffValidator` | the boundary gate: `parseAndValidate(rawJson) -> TaskResult`. Every agent routes its raw model output through this before `ResilientPipeline` ever sees a `TaskResult` object. A malformed payload is a hard `SchemaValidationException` — never silently coerced |
| `Retry` | retry-with-backoff for **one** transient call — wraps `OllamaClient`'s HTTP call only |
| `CircuitBreaker` | per-named-dependency 3-state breaker (`CLOSED`/`OPEN`/`HALF_OPEN`) — watches a **streak** of failures across many calls, opens after N consecutive failures, fails fast during the cooldown, allows one half-open trial call after cooldown elapses |
| `OllamaClient` | raw `HttpClient` + Jackson wrapper against `/api/generate`, same shape as `phase7-multi-agent/CoderAgent`'s embedded example — the one thing `Retry` wraps |
| `PlannerAgent` / `CoderAgent` / `ReviewerAgent` | the three-hop chain. Each builds a JSON-only prompt including a self-assessed `confidence`, calls `OllamaClient` (or a canned mock response under `-Dphase12.mock=true`), and returns `HandoffValidator.parseAndValidate(raw)` |
| `ResilientPipeline` | runnable `main` — chains Planner → Coder → Reviewer, each call wrapped in its own named `CircuitBreaker`, and gates every hop's result on `confidence` before forwarding it |

`A2AAgent`'s own javadoc names `PlannerAgent`/`CoderAgent`/`ReviewerAgent`/
`TesterAgent` as intended implementors; `TesterAgent` is deliberately not
built here (not referenced anywhere else) — this phase is about the
resilience layer around a chain, not a fourth hop.

## Running it

Run from **this** directory (`demos/phase11-resilient-pipeline/`).

```bash
mvn -o compile
mvn -o test

# mock mode (default recommendation) -- no live Ollama needed
mvn -o exec:java -Dphase12.mock=true

# live run -- requires Ollama running with qwen2.5-coder:7b pulled
mvn -o exec:java
```

`ResilientPipeline` runs three scenarios in sequence every time — there are
no CLI flags to pick one, they're cheap enough to always show all three:

1. **Happy path** — Planner → Coder → Reviewer, all three hops above the
   confidence threshold, completes end to end.
2. **Low-confidence abstention** — `ReviewerAgent` is constructed with
   `forceLowConfidence=true`; its mock response reports `confidence: 0.35`
   on a review of money-moving retry logic. The pipeline's
   `passesConfidenceGate` check (threshold `0.5`) trips and the run stops
   there instead of presenting a shaky review as a clean sign-off.
3. **Circuit breaker trip** — a chaos-mode `CoderAgent` (constructed with
   `chaos=true`, or triggerable standalone via `-Dphase12.chaosFail=true`)
   throws on every call, simulating a hard-down dependency. After 3
   consecutive failures its breaker opens; the 4th call fails fast with
   `CircuitOpenException` and the log line makes explicit that **no attempt
   was made** — the opposite instinct from `Retry`, which tries harder on
   one call rather than giving up on the dependency as a whole.

## What actually happened (real `mvn compile`/`mvn test`/`exec:java` output)

**Build: 10/10 tests pass, 0 failures, 0 errors**
(`CircuitBreakerTest` 4, `HandoffValidatorAbstentionTest` 5,
`CircuitBreakerChaosIntegrationTest` 1):

```
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0 -- in CircuitBreakerTest
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0 -- in HandoffValidatorAbstentionTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0 -- in CircuitBreakerChaosIntegrationTest
[INFO] Tests run: 10, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

**Scenario 2 — low-confidence abstention**, real terminal output:

```
--- Scenario 2: low-confidence abstention ---
  -> ReviewerAgent handling task 1e2b2491-... (type=code.review)
  [mock] ReviewerAgent returning canned completion (phase12.mock=true, no Ollama call)
  <- ReviewerAgent state=DONE confidence=0.35
  !! ReviewerAgent confidence 0.35 is below threshold 0.5 -- ABSTAINING: pipeline stops
     here rather than passing a low-confidence result onward.
```

The mock `ReviewerAgent` response driving this: a schema-**valid**
`TaskResult` (it passes `HandoffValidator` cleanly) that is nonetheless
flagged by the pipeline, because schema validity and confidence adequacy are
two different gates — a low-confidence result is still a well-formed
`TaskResult`, it's `ResilientPipeline.passesConfidenceGate`, not
`HandoffValidator`, that decides to abstain on it:

```json
{"taskId":"...","state":"DONE","artifacts":[{"type":"review","content":"This change touches money-moving retry logic; I was not able to trace every retry path with confidence in the context I was given."}],"issues":["possible double-charge on retry after a partial success -- needs a human to trace the transaction log"],"message":"reviewed but not confident this is safe to ship unchecked","confidence":0.35}
```

**Scenario 3 — circuit breaker trip**, real terminal output:

```
--- Scenario 3: circuit breaker trip (chaos-mode CoderAgent) ---
  -> CoderAgent(chaos) handling task chaos-task-1 (type=code.generate)
  call 1 -- attempted and failed: simulated CoderAgent outage (chaos mode) -- dependency is down [breaker state=CLOSED, streak=1]
  -> CoderAgent(chaos) handling task chaos-task-2 (type=code.generate)
  call 2 -- attempted and failed: simulated CoderAgent outage (chaos mode) -- dependency is down [breaker state=CLOSED, streak=2]
  -> CoderAgent(chaos) handling task chaos-task-3 (type=code.generate)
  [circuit:coder-agent-chaos] 3 consecutive failures -- OPENING (cooldown PT5S)
  call 3 -- attempted and failed: simulated CoderAgent outage (chaos mode) -- dependency is down [breaker state=OPEN, streak=3]
  -> CoderAgent(chaos) handling task chaos-task-4 (type=code.generate)
  call 4 -- FAILED FAST, no attempt made: circuit 'coder-agent-chaos' is OPEN -- failing fast, no call attempted (cooldown ends at 2026-08-10T08:06:51.435944400Z)
Final breaker state: OPEN (this run's failure streak=3)
```

Calls 1–3 each reach `CoderAgent.handle` and throw (the "attempted and
failed" log line proves the agent was actually invoked each time). Call 4 is
qualitatively different: the breaker refuses to invoke `chaosCoder.handle`
at all — the whole point of the OPEN state — and the log line says so
explicitly ("no attempt made"), which is exactly what
`CircuitBreakerChaosIntegrationTest.chaosCoderAgentTripsBreakerThenSubsequentCallFailsFast`
pins with an assertion, not just a print statement.

## Deviations from plan

- **`ResilientPipeline` always runs all three scenarios, no scenario-select
  CLI flags.** The brief left the exact shape open ("demonstrate the circuit
  breaker actually tripping"); three flag-gated scenarios like
  `phase9-agent-iam/AgentIamDemo` would work too, but since mock mode makes
  every scenario near-instant and side-effect-free, always running all three
  gives a more complete single-command demo without extra plumbing.
- **Chaos mode is a constructor parameter (`CoderAgent(client, true)`), not
  purely a system property.** `-Dphase12.chaosFail=true` is still honored
  (the no-arg-style `CoderAgent(client)` constructor reads it as its
  default), but `ResilientPipeline`'s scenario 3 uses the explicit two-arg
  constructor so the demo doesn't depend on a JVM-wide flag to show a
  deterministic breaker trip on every run.
- **`ReviewerAgent`'s low-confidence scenario is also a constructor
  parameter (`forceLowConfidence`)**, for the same reason: deterministic,
  scenario-scoped behavior without a global flag that would also affect
  scenarios 1 and 3.
- **`OllamaClient.generate` targets `/api/generate`** (single prompt-in,
  text-out), not `/api/chat` — matching the shape spelled out in the brief
  ("same shape as phase7-multi-agent/CoderAgent's embedded OllamaClient
  example"), which is the simpler of the two Ollama endpoints other phases
  use. `phase7-multi-agent`'s actual (non-embedded) `OllamaClient.java` uses
  `/api/chat` with message arrays and tool schemas — not needed here since
  each agent is a single generate-and-validate step, not a multi-turn
  tool-use loop.
- **`TesterAgent` was not built**, per the brief — `A2AAgent`'s javadoc
  names it as an intended implementor but it isn't referenced anywhere else
  in this phase's spec.

## What a production version would add

- **Real compounding-non-determinism math.** Gap 4 names this explicitly
  ("four 90%-accurate agents chained ≈ 66% overall success") and this demo
  doesn't model it at all — there's no measurement of each agent's actual
  success rate across many runs, no multiplication-through-the-chain
  calculation, and no answer to "how many hops can this pipeline tolerate
  before reliability degrades unacceptably." Confidence scoring here is a
  per-call self-report, not an empirically-measured, aggregated reliability
  number.
- **Distributed tracing across agent boundaries** — Gap 4 and AWS Module 4
  both call this out. Every hop here prints to stdout and that's the entire
  observability story; there's no correlated trace ID, no span timing, no
  export to anything an operator would actually query in production.
- **A real circuit-breaker library** (Resilience4j is the standard JVM
  choice) with per-breaker metrics, configurable failure-rate thresholds
  (not just a raw consecutive-failure count), and a sliding window instead
  of a simple streak counter.
- **A real JSON Schema validator** (draft 2020-12) instead of
  `HandoffSchema`'s hand-rolled field/type inspection — the moment a nested
  object, `oneOf`, or conditional rule shows up, hand-rolling stops being
  the pragmatic choice.
- **Persisted breaker state.** `CircuitBreaker` here is in-memory per JVM
  instance; a real multi-process/multi-instance deployment needs shared
  breaker state (or per-instance state plus a shared health signal) so one
  instance's breaker tripping actually protects the dependency from the
  whole fleet, not just that one process.

# Phase 15 — Demo: Capstone (the combined chain + the generated deck)

Goal: close the curriculum. Phases 0-14 each teach one mechanism in isolation;
this phase does the two things none of them can do alone — **run several of
those mechanisms together in one process**, and **synthesize the whole
curriculum into one artifact you can actually present** (`SLIDES.md`, one
Mermaid diagram per phase, generated from source).

**What "capstone" means here, precisely.** It is not a new technique and not a
production system. It is two deliverables:

1. `CapstoneDemo` — one task (*draft the on-call handover note for incident
   INC-4471*) pushed through six phases' techniques in a single deterministic,
   offline run, so the seams between them are visible: where the credential
   stops the tool call, where the schema gate stops the handoff, where the
   confidence gate stops the pipeline, and where the eval layer reads the trace
   rather than the agent's own account of itself.
2. `SlideDeckGenerator` → `SLIDES.md` — 17 slides generated from
   `PhaseSummary.ALL`, so the deck and the phase data cannot drift apart.

**It is NOT a production agent, and it is not even as capable as the phases it
draws from.** There is no live model anywhere in this module — the agent's
"reasoning" is a `switch` statement. See "What this simplifies away" for the
full list; that section is longer than the architecture table on purpose.

This is a fully independent Maven module (own `pom.xml`, flat class structure),
same convention as every other phase — **no imports from any sibling phase**.
Every pattern below was re-implemented here in its minimal form, the same
"copy in and adapt" discipline phases 9-14 use.

## Which phase each piece comes from

| This module | Copied/adapted from | What was kept | What was dropped |
|---|---|---|---|
| `ScopedCredential`, `CredentialBroker` | [`phase9-agent-iam`](../phase9-agent-iam/) | principal chain, scope subset check, expiry capped at parent | `issuedAt`, audit log, random tokens (deterministic ids instead) |
| `Tool`, `SecureToolInvoker` | [`phase3-tool-calling`](../phase3-tool-calling/) + [`phase9-agent-iam`](../phase9-agent-iam/) | schema/executor tool shape, `requiredScope`, credential passed out-of-band, expiry-checked-before-scope | `AuditLog`, `redactCredentialLeak` |
| `Guardrails` | [`phase4-agents`](../phase4-agents/) | iteration cap, `(tool, args)` loop detection | token budget, destructive-action confirm hook |
| `SummarizerAgent` | [`phase4-agents`](../phase4-agents/) `AgentLoop` | plan → act → observe structure, terminal step, handoff payload | **the model call** — replaced by a decision table |
| `TaskResult`, `HandoffValidator`, `SchemaValidationException` | [`phase11-resilient-pipeline`](../phase11-resilient-pipeline/) | hand-rolled field/type/enum/range checks, `confidence`, hard-fail on malformed | typed `Artifact` records, circuit breaker, `Retry` |
| `Embeddings`, `RankedMemory` | [`phase13-agent-memory-v2`](../phase13-agent-memory-v2/) | ranked + bounded + time-decayed recall, decay reset on recall | JSON persistence, character trigrams, ANN index, BM25/RRF/rerank, graph memory |
| `RunTrace` | [`phase4-agents`](../phase4-agents/) `trace.jsonl` + [`phase8-autonomous`](../phase8-autonomous/) `ObservabilityCollector` | append-only event log, JSONL rendering | writing to disk (in-memory, printed only) |
| `CapstoneEval` | [`phase12-eval-harness`](../phase12-eval-harness/) | structural conformance rules + deterministic 0-100 composite | LLM-as-judge, golden/regression datasets, cost/latency aggregation |
| `PhaseSummary`, `SlideDeckGenerator` | new to this phase | — | — |

Phases whose ideas are represented **only in the deck**, not in the running
code: [0](../phase0-tokens-inference/) (tokens/sampling),
[1](../phase1-local-serving/) (serving/quantization),
[2](../phase2-prompting-rag/) (RAG),
[5](../phase5-skills/) (skills),
[6](../phase6-mcp/) (MCP),
[7](../phase7-multi-agent/) (A2A),
[8](../phase8-autonomous/) (HITL gates),
[10](../phase10-frameworks-protocols/) (agent-card discovery, x402),
[14](../phase14-domain-specialization/) (the four levers). Chaining all
fifteen would have meant an HTTP server, an MCP transport, a live model and a
git repo in one demo — a lot of moving parts to demonstrate a point six
already make.

## Architecture

| Class | Role |
|---|---|
| `PhaseSummary` | the curriculum as data — `ALL` is 15 records (phase 0-14), each with `keyConcept`, `takeaway` and a hand-written Mermaid diagram; plus `COMBINED_ARCHITECTURE` for the closing slide |
| `SlideDeckGenerator` | `main` — renders `PhaseSummary.ALL` to `SLIDES.md`: title slide, 15 phase slides, closing combined-architecture slide, `---` separated, Mermaid in fenced blocks |
| `ScopedCredential` / `CredentialBroker` | phase-9 credential layer: `mintRoot` then `delegate`, narrowing scopes and capping expiry at the parent |
| `Tool` / `SecureToolInvoker` | phase-3/9 tool layer: `Tool.schema()` is what a model would see (no `requiredScope`, no credential field), `invoke` takes the credential as a separate Java parameter |
| `Guardrails` | phase-4 engine rails: iteration cap and `(tool, args)` loop detection |
| `SummarizerAgent` | the deterministic agent — `plan(iteration)` proposes steps, `buildHandoffJson` emits the raw handoff payload; `Mode` picks which of the three runs' behaviour it exhibits |
| `TaskResult` / `HandoffValidator` / `SchemaValidationException` | phase-11 handoff gate: parse-and-validate or throw, never coerce |
| `Embeddings` / `RankedMemory` | phase-13 memory: hashed bag-of-words vectors, cosine × exponential decay ranking, hard bound of 3 results |
| `RunTrace` | append-only event log the eval layer reads (`credential_minted`, `tool_call`, `handoff`, `memory_write`, `memory_recall`, `run_summary`) |
| `CapstoneEval` | phase-12 scoring: `violations(trace)` (5 structural rules) and `score(trace)` (deterministic 0-100) |
| `CapstoneDemo` | runnable `main` — runs the chain three times and prints the comparison |

## Running it

Run from **this** directory (`demos/phase15-capstone/`). No Ollama, no
network, nothing to install beyond Maven and a JDK 17+.

```bash
mvn -o compile
mvn -o test

# the combined demo (this module's exec.mainClass)
mvn -o exec:java

# regenerate SLIDES.md from PhaseSummary.ALL
mvn -o compile exec:java -Dexec.mainClass=SlideDeckGenerator
```

`SLIDES.md` is committed, so you do not have to run the generator to read the
deck — GitHub renders the `mermaid` fences natively. `---` as a slide
separator is the convention Marp and reveal-md both read, but **nothing here
depends on either**; the file is a plain Markdown document first.

The three runs `CapstoneDemo` executes differ in exactly one thing — what
`SummarizerAgent` hands off:

| Run | Mode | Where it stops | Score |
|---|---|---|---|
| A | `NORMAL` | nowhere — completes | 93.87 → PASS |
| B | `LOW_CONFIDENCE` | confidence gate (0.35 < 0.50) | 27.00 → BLOCK |
| C | `MALFORMED_HANDOFF` | schema gate (`confidence` is a String) | 0.00 → BLOCK |

## What actually happened (real `mvn -o test` / `exec:java` output)

**Build: 18/18 tests pass, 0 failures, 0 errors** (`CapstoneEvalTest` 8,
`ConfidenceGateAbstentionTest` 5, `CredentialDelegationTest` 5):

```
[INFO] Tests run: 8, Failures: 0, Errors: 0, Skipped: 0 -- in CapstoneEvalTest
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0 -- in ConfidenceGateAbstentionTest
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0 -- in CredentialDelegationTest
[INFO] Tests run: 18, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

**Run A, steps 1-3** — the credential narrows, then denies a step the agent
genuinely wanted to take:

```
[1/6] scoped credential issuance                      (phase 9 -- agent IAM)
      mintRoot   cred-1  chain=EndUser  scopes=[read:incident, send:email, delete:incident]  expires=2026-08-10T09:15:00Z
      delegate   cred-2  chain=EndUser -> OrchestratorAgent  scopes=[read:incident, send:email]
      delegate   cred-3  chain=EndUser -> OrchestratorAgent -> SummarizerAgent  scopes=[read:incident]
      ttl asked 30m, expiry granted 2026-08-10T09:10:00Z -- capped at the parent's; a delegate
      cannot mint itself more time or more scope than it was handed.
      blast radius: 1 of 3 capabilities reachable if SummarizerAgent is owned
      token never enters the model's context; logged as ***cret only

[2/6] agent loop: plan -> guardrails                  (phase 4 -- agent loop)
[3/6] act: credential-gated tool call                 (phase 3 + 9 -- tools)
      tools the model can see: [read_incident, send_email, delete_incident]
      note: Tool.schema() omits requiredScope -- the model is never told
      which capability a tool needs, and cannot ask for a credential.
      iter 1  plan: I cannot summarize an incident I have not read; fetch the record first.
      iter 1  act: read_incident{"id":"INC-4471"} -> AUTHORIZED (needs read:incident)
      iter 1  observe: INC-4471 checkout latency p99 4.2s from 08:12 to 08:40 UTC, cause: ...
      iter 2  plan: The handover should reach the next on-call directly; try to email it.
      iter 2  act: send_email{"to":"oncall@example.com"} -> DENIED (scope 'send:email' not granted to SummarizerAgent)
               the executor never ran -- denial happens before it,
               not inside it, so there is nothing to undo.
      iter 3  plan: Email was refused by my credential scope. Return the summary as an artifact and flag the unsent notification as an issue.
      iter 3  no further tool needed -- handing off
```

The `send_email` denial is the most useful line in the whole demo. The agent's
plan was reasonable; the credential simply did not authorize it, the executor
never ran, and the agent carried on and recorded the failure as an issue on the
handoff instead of pretending the email went out. That is the phase-9 lesson
and the phase-4 lesson meeting in one step.

**Run A, steps 5-6** — ranked recall, then the trace and the score:

```
[5/6] memory: ranked, bounded, decayed                (phase 13 -- memory v2)
      4 prior-run facts already stored
      wrote 2 facts from this run
      recall("what caused the checkout latency incident INC-4471 and who owns the handover", topK=5)
      asked for 5, hard bound is 3 -- got 3
        0.4273 = sim 0.4537 x decay 0.9418  [this-run] INC-4471 root cause: a connection-pool change; checkout latency now recovered
        0.3820 = sim 0.4573 x decay 0.8353  [prior-run] INC-4102 root cause: expired TLS certificate on the payments gateway; renewed and expiry aler...
        0.3774 = sim 0.3889 x decay 0.9704  [this-run] INC-4471 handover owner: the payments squad, next check 14:00 UTC

[6/6] evaluation over this run's own trace            (phase 12 -- eval harness)
        {"run":"...","event":"credential_minted","credentialId":"cred-3","principal":"SummarizerAgent","scopes":"read:incident"}
        {"run":"...","event":"tool_call","tool":"read_incident","requiredScope":"read:incident","authorized":true,"executed":true}
        {"run":"...","event":"tool_call","tool":"send_email","requiredScope":"send:email","authorized":false,"executed":false}
        {"run":"...","event":"handoff","schemaValid":true,"confidence":0.86,"forwarded":true}
        {"run":"...","event":"memory_write","facts":2}
        {"run":"...","event":"memory_recall","requested":5,"returned":3,"fromThisRun":2}
        {"run":"...","event":"run_summary","outcome":"COMPLETED"}
      structural violations: none
      composite score: 93.87 / 100   CI gate at 70 -> PASS
```

Note the middle row of the recall: **an unrelated prior incident (INC-4102)
outranks one of this run's own facts** on the strength of shared words like
"root cause" and "payments". That is not a bug in the demo, it is what a
64-dimension hashed bag-of-words with no semantics does, and it is exactly why
phase 13 builds BM25 + vector + RRF + rerank on top rather than shipping plain
cosine. The demo prints this observation itself rather than quietly picking
wording that hides it.

**Run B — abstention:**

```
      CONFIDENCE GATE: 0.35 < 0.50 -- ABSTAINING
      the result was schema-VALID; those are two different questions.
      nothing is written to memory and nothing is forwarded -- a shaky
      handover note that reads as authoritative is worse than none.
        unresolved: could not email the handover: scope 'send:email' not granted to SummarizerAgent
        unresolved: the incident record does not say whether the connection-pool change was reverted everywhere or only in eu-west-1
      step 5 (memory) never runs -- an abstention that still writes to
      memory is not an abstention (CapstoneEval re-checks this off the trace).
```

**Run C — schema rejection:**

```
      SCHEMA GATE: REJECTED -- [field 'confidence' must be a number, got STRING]
      hard stop. The payload is not coerced into a default TaskResult;
      a malformed handoff is a bug to fix, not a value to guess at.
```

**The comparison, which is the actual point of running all three:**

```
run                           score      CI gate violations
A (confident)                 93.87         PASS none
B (low confidence)            27.00        BLOCK none
C (malformed handoff)          0.00        BLOCK none
```

All three runs have **zero structural violations** while two of them score
badly. That pairing is deliberate: the score answers *"did this run produce a
shippable result?"* and the violation list answers *"did the machinery behave
correctly?"*. Abstaining on a shaky summary is correct behaviour **and** an
unshippable run, and a scorer that cannot express both at once will eventually
wave one of them through. `CapstoneEvalTest.abstainedRunScoresBelowTheGateButHasNoViolations`
pins exactly this.

## What this simplifies away

Read this section before quoting any of the above as an accomplishment.

- **No LLM. Anywhere.** `SummarizerAgent.plan` is a `switch` over the iteration
  number. The loop's *structure* is real (steps are proposed, guardrails
  approve, tools run under a credential, observations feed forward, the run
  ends in a handoff that must survive validation) but the only genuinely hard
  part of an agent — that the proposed step might be nonsense — is absent by
  construction. Phases 4, 7, 8 and 14 call a live model precisely because that
  is where the difficulty lives. The tradeoff bought here is that
  `mvn -o exec:java` works on any machine with a JDK, forever, with identical
  output; for a capstone whose job is to show the *seams between* techniques,
  that was worth more than another live-model run.
- **Confidence is a constant, not a measurement.** `0.86` and `0.35` are
  hardcoded in `SummarizerAgent`. A real self-assessed confidence comes from
  the model and is itself of questionable reliability — phase 11's README is
  blunt about this and nothing here improves on it.
- **No HTTP, no A2A, no MCP, no agent-card discovery.** The "orchestrator" is a
  string in a principal chain, not a process. Phase 10 has the real sockets.
- **One agent, not a pipeline of them.** Phase 11 chains
  Planner → Coder → Reviewer with a circuit breaker per hop; this module has a
  single agent and **no circuit breaker at all** — there is no flaky dependency
  to break on when every call is a local function.
- **No human-in-the-loop gate.** Phase 8's GATE1/GATE2 are not modeled here;
  the confidence gate stops the run but nothing routes it to a person.
- **The memory is a toy.** In-memory only (nothing persists between runs), 64
  hashed dimensions, no ANN index, no BM25, no rerank, no graph. See the recall
  output above for the ranking artifact this produces.
- **The eval layer is one-third of phase 12.** Structural conformance and a
  composite score, yes; no golden dataset, no regression suite, no adversarial
  set, no LLM-as-judge, no cost/latency aggregation. And the score's weights
  (50/20/20/10/−15) are hand-tuned numbers, not calibrated against anything.
- **`PhaseSummary.ALL` can go stale.** It is a hardcoded snapshot of what the
  sibling phases contain, chosen over reading their sources at runtime because
  cross-module reads would break this module's portability. If phase 13 is
  rewritten tomorrow, nothing here will notice. There is no test asserting the
  deck matches the phases, because there is nothing machine-readable to assert
  it against.
- **Determinism is engineered, and that is itself a simplification.**
  `CredentialBroker` mints `cred-1`, `cred-2`, `cred-3` from a counter and
  `tok-N-secret` as tokens; `ScopedCredential` uses a `LinkedHashSet` rather
  than `Set.copyOf` specifically because the latter randomizes iteration order
  per JVM and would make the printed output differ between runs. Two
  consecutive `exec:java` runs are byte-identical — good for a demo you diff,
  and **exactly what a real credential broker must not do**: a predictable
  token is not a secret.

## Notes on the design

- **The three gates are the architecture.** Credential scope, schema validity,
  and confidence are three independent checks that fail in three different
  ways, and the demo exists to make that legible: a scope failure stops one
  *step* and the run continues; a confidence failure stops the *run* and
  nothing is stored; a schema failure stops the *handoff* and there is nothing
  to even evaluate. None of them can substitute for another, which is why
  `ConfidenceGateAbstentionTest` pins the schema gate and the confidence gate
  as separate mechanisms over the same payload.
- **The eval layer reads the trace, never the summary.** This is the lesson
  phase 4, 8 and 12 all arrive at independently. `CapstoneEval.violations` can
  catch `UNAUTHORIZED_EXECUTION` (an executor that ran without an authorizing
  scope) because `SecureToolInvoker` records `authorized` and `executed` as two
  separate facts at the moment they happen. If the invoker were trusted to
  report "everything went fine", there would be nothing to check it against.
  `CapstoneEvalTest` builds hand-written traces that violate each rule, rather
  than only testing the traces the demo happens to produce — a validator that
  has only ever seen valid input is not a validated validator.
- **The deck is generated for the same reason the trace is recorded.** One
  source of truth (`PhaseSummary.ALL`), one derived artifact (`SLIDES.md`),
  regenerable on demand. Hand-maintaining 17 slides next to the same facts in
  Java would guarantee they disagree eventually.

## What a production version would add

Everything in "What this simplifies away", plus: a real model behind
`SummarizerAgent.plan`, revocable and unguessable credentials, distributed
tracing across the steps instead of an in-process event list, persisted memory
with a real embedding model, a golden dataset so the composite score has
something to regress against, and a human-review path for exactly the runs that
abstain — because an abstention that nobody is paged about is just a task that
silently did not happen.

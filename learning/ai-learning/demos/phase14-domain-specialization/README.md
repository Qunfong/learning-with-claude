# Phase 14 — Demo: Customer Support Domain Specialization (Gap 7)

Goal: close Gap 7 from `learning/ai-learning-gap-review/NOTES.md` ("Domain
specialization (customer-facing, geospatial, voice) — not explored at all").
Every demo in `phase0-14` is a generic dev-tooling / DevOps-flavored agent —
coding assistants, code review, MCP file/receipt servers, credential
brokering — none of them adapt a system prompt, knowledge corpus, tool
scope, or guardrails to an actual business vertical the way AWS Module 5
teaches. This phase builds one, using AWS's **four levers of domain
specialization** (system prompt, knowledge corpus, tool selection,
guardrails) applied to a single vertical: customer support for a fictional
online electronics retailer.

**This models ONE of AWS Module 5's three verticals and is NOT a substitute
for the other two, or for a production support bot.** AWS Module 5 covers
customer engagement/sales (intent classification, personalization,
escalation design), location-aware/logistics agents (why models shouldn't
do spatial computation themselves — hand it purpose-built geospatial tools
instead; freshness/caching tradeoffs for traffic vs. static geo data), and
voice-enabled agents (STT/TTS pipeline, strict latency budgets, response-
length constraints, interruption handling, multilingual design). This demo
only builds the first. **Geospatial is entirely untouched** — there's no
tool here that does spatial/routing computation at all, correct or
otherwise, because the vertical picked (support tickets) never needed one;
the "don't let the model do spatial math itself" principle isn't
demonstrated or violated, it's just absent. **Voice is entirely
untouched** — this demo is text-in/text-out over plain HTTP; there is no
STT/TTS integration, no latency budget enforced anywhere in the code (a
single Ollama call here can take several seconds, which would be a hard
failure under voice's sub-2-second constraint), no response-length shaping
for spoken output, and no interruption handling. If you need either of
those two verticals, this phase gives you no mental model for them — go to
the AWS material directly.

This is a fully independent Maven module (own `pom.xml`, flat class
structure), same convention as every other phase — no imports from
`phase4-agents/` or `phase13-agent-memory-v2/`. `Tool.java` and
`Guardrails.java` are copied in from `phase4-agents` **unchanged** (generic
engine-level plumbing: the schema+executor tool shape, and the
maxIterations/loopDetection engine rails that apply to any agent loop
regardless of domain). `OllamaClient.java` and `AgentLoop.java` are trimmed
re-derivations of `phase4-agents`' versions — see "Deviations" below for
exactly what was cut and why.

## Architecture: four levers, one comparison harness

| Class | Role | Which lever |
|---|---|---|
| `KnowledgeCorpus` | hardcoded `TEXT` block: refund policy ($100 auto-approval cap), shipping policy, two known technical issues (export crash, login loop) with workarounds, escalation policy (repeat contact / legal-chargeback mention / over-cap refund), competitor-comparison policy | **Lever 2 — knowledge corpus** |
| `DomainGuardrails` | record: `maxAutoRefund` (100.00), `competitorNames`; `competitorPricingRefusal(...)` — deterministic pre-model refusal; `exceedsAutoApprovalCap(...)` — checked a second time inside `SupportTools.issueRefund`, so the cap holds even if the guardrail call site were skipped | **Lever 4 — guardrails** |
| `SupportTools` | 3 tools: `lookup_order`, `issue_refund` (hard-denies over-cap amounts in the executor itself, not by prompt instruction), `escalate_to_human`; instantiated once per agent so the domain and baseline runs never share `escalationLog`/`refundLog` | **Lever 3 — tool selection** |
| the `systemPrompt` local variable inside `DomainAgentDemo.runDomainAgent` | inline text block: 3-step instruction (classify intent silently → act with tools, grounded only in the corpus → force a real `escalate_to_human` call on any policy trigger, never just claim one happened), concatenated with `KnowledgeCorpus.TEXT` | **Lever 1 — system prompt** (no separate class for this lever — it's a `String` built at call time, contrasted directly against the one-line baseline prompt) |
| `DomainAgentDemo` | runnable `main`; 4 scenarios, each run twice — once through the domain agent (all four levers applied), once through a baseline agent (generic "helpful assistant" prompt, same tools, same model, no guardrail, no corpus) | the comparison harness / negative control |
| `AgentLoop` | trimmed plan → act → observe → repeat loop, adapted from `phase4-agents/AgentLoop`; no `trace.jsonl` (console logging only — two agents interleaved in one process would produce a confusing shared trace file) | domain-agnostic engine |
| `Guardrails` | copied unchanged from `phase4-agents`; generic **engine**-level rails (`maxIterations`, `loopDetection`) shared by both the domain and baseline agent — explicitly **not** the domain guardrails (see `DomainGuardrails` for those) | domain-agnostic engine |
| `OllamaClient` | trimmed HTTP client to Ollama's `/api/chat`; no retry/backoff wrapper (that's `phase4-agents`' lesson, orthogonal to this one) | domain-agnostic engine |
| `Tool` | copied unchanged from `phase4-agents/Tool.java`: `name`/`description`/`parameters`/`destructive`/`executor` record | domain-agnostic engine |

## Running it

Run from **this** directory (`demos/phase14-domain-specialization/`).
Requires a local Ollama server at `localhost:11434` with `llama3.2:3b`
pulled (`ollama pull llama3.2:3b`). **There is no mock/offline flag** —
`DomainAgentDemo.main` always calls the live model for every scenario, for
both the domain and baseline agent. `mvn test` does not touch
`DomainAgentDemo` at all; it only runs the deterministic, model-free
`DomainGuardrailsTest`/`SupportToolsTest`.

```bash
mvn -q compile
mvn -q test

# all four scenarios, domain agent then baseline agent, back to back
mvn -q compile exec:java
```

## What it actually demonstrates

Both agents share the exact same model (`llama3.2:3b`), temperature
(`0.2`), and tool implementations (`SupportTools`, separately instantiated
per agent). The *only* variable across the two runs of each scenario is
whether the four levers are applied — that's the point: isolate the levers
as the explanatory variable, not the model or the tools.

1. **Over-cap refund request** ($500 on `ORD-2002`, a keyboard) — expected
   intent `billing`. The domain agent's corpus states the $100 cap and its
   system prompt's STEP 3 forces a real `escalate_to_human` call the moment
   `issue_refund` returns `DENIED`, before saying anything to the customer.
   The baseline agent has no idea a cap exists beyond whatever
   `issue_refund` itself returns in that one call, and no instruction to
   escalate — this scenario is meant to show whether "policy lives only in
   the tool's return string" is enough to get correct behavior from a
   generic prompt, versus a prompt that's told the policy up front and told
   what to do about a denial.
2. **Technical support** (app crash on Export) — expected intent
   `technical`. The domain agent's corpus has the exact known-issue entry
   (versions 2.0-2.3, fixed in 2.4, batch-export workaround); there is no
   tool that looks up known issues, so grounding depends entirely on
   whether that text is present in context. The baseline agent never sees
   this text at all — this isolates the knowledge-corpus lever specifically.
3. **Escalation-worthy complaint** (login loop, third contact, chargeback
   threat) — expected intent `escalation`. The corpus's escalation policy
   explicitly triggers on repeat contact *and* on chargeback/legal mentions
   — this scenario hits both triggers at once. The domain agent's prompt
   should force `escalate_to_human`; the baseline agent has no escalation
   policy anywhere in its context and no instruction to call the tool for
   this kind of complaint.
4. **Competitor pricing** (guardrail probe) — expected intent `billing`.
   For the domain agent, `guardrails.competitorPricingRefusal(...)` is
   checked in `DomainAgentDemo.runDomainAgent` **before the model is called
   at all** — if it fires, the canned refusal is printed and the model
   never sees the message. This is the one case in the whole demo that is
   100% reproducible regardless of model mood, by construction. The
   baseline agent has no such guardrail and no reason not to answer a
   direct pricing-comparison question.

Taken together, the four scenarios are meant to show that the levers
produce measurably different behavior from a same-model, same-tools
baseline: intent-appropriate tool sequencing, a refund cap that's actually
enforced twice (guardrail check + tool-level hard denial), forced
escalation on policy triggers instead of the model deciding for itself, and
one case where the "guardrail" isn't even a model behavior — it's a
`String` check that runs before the model is invoked.

## Deviations from AWS Module 5

- **Only 1 of 3 verticals.** Geospatial and voice are not built at all —
  see the callout above for specifics on what's missing from each
  (spatial-tool delegation for geospatial; STT/TTS, latency budget,
  interruption handling for voice).
- **No domain-expert-validated golden eval dataset.** `SCENARIOS` is 4
  hand-picked scenarios with an `expectedIntent` label that's printed for a
  human to eyeball in the console output — there's no automated scoring
  loop, no accuracy metric aggregated across runs, and no actual customer-
  support domain expert reviewed the corpus or the scenarios. AWS Module 5
  explicitly calls out domain-representative golden datasets and domain-
  expert involvement as part of domain-specific evaluation; neither exists
  here.
- **No real knowledge-base ingestion pipeline.** `KnowledgeCorpus.TEXT` is
  a hardcoded Java text block, not a vector store or RAG pipeline. That
  machinery already exists elsewhere in this repo (`phase2-prompting-rag`'s
  brute-force cosine `VectorStore`) and is being built out properly in the
  sibling `phase13-agent-memory-v2` (long-term semantic memory, HNSW-style
  indexing) — `KnowledgeCorpus`'s own javadoc notes this phase deliberately
  does not depend on that module existing, per the repo's one-module-per-
  phase convention.
- **No `trace.jsonl`.** `AgentLoop` drops `phase4-agents`' persisted trace
  file in favor of console logging only, specifically because running the
  domain and baseline agent back to back in one process would interleave
  two agents' events into one confusing file.
- **No retry/backoff.** `OllamaClient` drops `phase4-agents`'
  `Retry.withBackoff` wrapper; a transient Ollama error just throws.
- **No mock/offline mode.** Unlike some other phases, there is no CLI flag
  or system property to run `DomainAgentDemo` without a live Ollama model —
  `mvn test` only reaches the deterministic `SupportTools`/
  `DomainGuardrails` layer.
- **Intent classification isn't structurally checked.** The domain
  prompt's STEP 1 ("silently classify the customer's intent") is a
  prompting technique, not a classifier with a parsed, checked output —
  nothing in the code extracts or validates what intent the model actually
  picked against `Scenario.expectedIntent()`. That comparison is left to
  whoever reads the console output.
- **Same model/tools/temperature by design.** Deliberately holds the model
  constant so the four levers are the only variable — but that also means
  this demo says nothing about a real product tradeoff AWS's material
  touches on: whether a smaller model with strong domain framing can beat a
  larger model with weak framing.

## Tests

`DomainGuardrailsTest` (5 tests, all deterministic, no model calls):
competitor name + pricing language together trigger a refusal that names
the competitor; a competitor mentioned alone (no pricing language) does not
trigger it; a pricing question that names no competitor does not trigger
it; the refund cap boundary is inclusive ($100.00 not exceeded, $100.01
exceeded); the standard cap is $100.

`SupportToolsTest` (7 tests): refunds at or exactly at the cap are
auto-approved; **an over-cap refund is hard-denied regardless of caller or
phrasing** — the load-bearing assertion for the whole phase, proving the
cap is enforced by tool code and not by hoping the model respects the
prompt; `lookup_order` returns known order data and a `FOUND_NONE`
sentinel for an unknown order; `escalate_to_human` logs its reason;
per-agent `SupportTools` instances keep independent escalation/refund logs
(the guarantee that lets `DomainAgentDemo` run the domain and baseline
agent back to back on the same scenario without their evidence bleeding
together).

`DomainAgentDemo` itself is not a JUnit test — it's a live-model manual
`exec:java` run, the same split `phase4-agents` uses (`RetryTest` is its
only JUnit test; `CodingAgentDemo`/`GuardrailsDemo` are live-model
main-class runs).

# Phase 4 — Agents

## What This Phase Is About

Phase 3 built a single tool call: schema in, structured request out, validate, execute, done. An agent is what you get when that call happens in a loop the model itself drives — plan, act, observe, decide what's next — until the task is done or a guardrail stops it. Phase 4 builds that loop, gives it memory that outlives a single run, and hardens it against the ways an ungoverned loop actually fails in practice, not in theory.

Phase 5 (skills) and Phase 6 (MCP) both inject into "the Phase 4 agent" as their own build target. Phase 7 (multi-agent/A2A) composes this phase's agent loop with Phase 5's skill and Phase 6's MCP client into two agents that collaborate. Nothing here is throwaway — the `AgentLoop` this phase produces is the foundation the rest of the curriculum builds on.

---

## Core Concepts

### The Loop, Not the Tool, Makes It an Agent

```
PHASE 3: ONE TOOL CALL              PHASE 4: THE LOOP
──────────────────────────          ──────────────────────────────
schema → model decides →            schema → model decides → act →
validate → execute →                observe → model decides again →
summarize → DONE                    act → observe → ... → DONE
(exactly one round trip)            (as many round trips as the
                                      model needs, bounded by guardrails)
```

The building blocks (schema, structured `tool_calls`, validate-before-execute) are identical to Phase 3. The only new thing is the `while` loop that keeps feeding tool results back in until the model stops asking for tools — plan → act → observe → repeat.

### Memory: Short-Term vs Long-Term

```
SHORT-TERM (session)                LONG-TERM (persistent)
──────────────────────────          ──────────────────────────────
The growing messages[] list         A file/store the agent writes to
inside ONE AgentLoop.run() call     via an explicit tool call
                                     (remember/recall — just a Tool,
Lives as long as the JVM            no special engine support)
process is running
                                     Survives the process ending —
Gone the instant the process        a NEW process, with an EMPTY
exits — no special code needed      messages[] list, can still
to "forget" it                      retrieve it

RULE OF THUMB:
"Did the model say this earlier in THIS conversation?" → short-term
"Does the agent need this in a conversation it hasn't had yet?" → long-term
```

Long-term memory is not a vector DB or embeddings here (that's Phase 2's territory) — it's a flat file behind a `remember`/`recall` tool pair. The point is the *mechanism* (persistence is a tool call, not magic), not retrieval quality.

### Guardrails — Each One Catches a Different Failure

```
FAILURE MODE                        GUARDRAIL THAT CATCHES IT
──────────────────────────          ──────────────────────────────
Model never produces a              maxIterations — hard cap,
final answer                        independent of model cooperation

Every turn re-sends the full        tokenBudget — halts before the
history through the tokenizer;      next call once cumulative usage
cost grows unboundedly              crosses a line

Model repeats the exact same        loopDetection — compares each
tool call over and over with        call to the immediately
no new information                  preceding one, aborts on a match

Model wants to call a               confirmHook — gates execution on
destructive/irreversible tool       explicit approval; answers "may
                                     this happen?", not "is this right?"
```

These are complementary, not redundant — a confirm-hook that approves a destructive call doesn't protect against that call's *content* being wrong (see the coding-agent build below). Guardrails bound damage; they don't fix a badly specified goal.

### Error Handling & Retries — a Different Layer Than Guardrails

```
GUARDRAILS                          RETRIES
──────────────────────────          ──────────────────────────────
Bound the AGENT LOOP                Bound a SINGLE CALL BOUNDARY
(how many turns, how much           (one HTTP request, one tool
 spend, is this repeating)          execution)

The agent loop is aware of          The agent loop is NOT aware —
guardrails firing (it's the         retries happen underneath a
thing being bounded)                call and either succeed or
                                     surface a final failure
```

Only *transient* failures (timeouts, 5xx) should be retried, with backoff, and a bounded attempt count. A permanent failure (bad request, missing argument) retried anyway just wastes attempts on something that will never succeed.

### Trust the Trace, Not the Summary

The single most important empirical finding from building this phase: a small local model's own natural-language description of what it just did **cannot be trusted** — not maliciously, just unreliably. It can claim a file was fixed when `write_file` was never actually called; it can claim `recall` found nothing when the tool result plainly contained the answer. Structured, independent tracing (every real tool call logged with its actual arguments and result, separate from the model's summary) is what makes this catchable at all instead of silently shipping a wrong belief.

---

## Build Specification

### Demo Sequence (one Maven module, `phase4-agents`, shared `AgentLoop` engine)

**1. AgentLoopDemo — the naive loop**
An unsatisfiable goal, no guardrails configured (`Guardrails.none()`). Demonstrates the loop has no built-in reason to stop, and documents whatever the model actually does (observed: batches repeated identical calls, then falsely claims completion) rather than a hypothesized failure.

**2. GuardrailsDemo — the same task, bounded**
Each guardrail (max-iterations, token-budget, loop-detection, confirm-hook) demonstrated in isolation against the same unsatisfiable task, then combined and re-run against `AgentLoopDemo`'s exact scenario to show the outcome actually changes.

**3. MemoryDemo — short-term vs long-term, proven across two processes**
Run once: the agent stores a fact via `remember`. Run again (separate process, empty message history): the agent retrieves it via `recall`. The distinction has to be *felt* — a fresh process with the right answer anyway — not just described.

**4. CodingAgentDemo — the capstone**
The actual build target: a mini Claude-Code-style loop with `read_file`, `write_file`, `run_tests` tools operating on a real (small) Java file with a real failing test, sandboxed to its own `workspace/` directory with a path-traversal guard. The agent reads the failure, edits the source, reruns the real test (a real `javac`/`java` subprocess, not a simulation), and repeats until it passes or a guardrail halts it. `run_tests` also simulates one transient failure per run so retry-with-backoff is deterministically exercised. Every tool call is logged to `trace.jsonl`, independent of the model's own summary.

### What "Read/Edit Files and Run Tests" Actually Exposed

This is the part worth treating as a finding, not just a build note: giving a small model a large, unstructured argument (an entire file's contents) is where reliability collapses. Observed failure modes, live-verified, not hypothetical:
- The model calls `write_file` with genuinely corrupted content (escaping errors) — caught by a real compile failure.
- The model **never calls `write_file` at all**, but its final text claims it did and that tests passed — only catchable by comparing the structured trace (and the file on disk) against the claim.

This is exactly why production coding agents work with diffs/patches instead of "rewrite the whole file": a diff is a dramatically smaller, simpler argument to get right than an entire file.

---

## Open Questions to Explore

1. If a diff/patch-based `write_file` replaced whole-file rewrite, would failure mode 2 (fabricated success) still occur, or is it specific to large-argument tool calls?
2. At what point does `tokenBudget` become the *binding* guardrail instead of `maxIterations` — does that threshold depend on task complexity, model size, or both?
3. `MemoryDemo`'s `recall` returns everything unranked. At what stored-fact count does that stop being "the simplest thing that works" and start actively hurting the agent (context bloat, contradictory old facts)?
4. The confirm-hook auto-approves in this demo. What's the smallest realistic policy-engine (not a human in the loop) that could make a real approve/deny decision instead?
5. `RUNAWAY_SAFETY_CEILING` is deliberately not a `Guardrails` feature — it's demo-runner self-protection. Is that distinction actually load-bearing anywhere outside a tutorial, or is "no guardrail configured" always in practice bounded by *something* (infra timeout, OOM, budget alert)?

---

## Success Criteria

- [ ] `AgentLoopDemo` demonstrably fails without guardrails (documented from a real run, not assumed)
- [ ] `GuardrailsDemo` demonstrates all four guardrails in isolation, then combined against the naive demo's exact scenario, with a visibly different outcome
- [ ] `MemoryDemo` proves long-term memory survives a process restart while short-term memory doesn't, across two real separate runs
- [ ] `CodingAgentDemo` actually compiles and runs a real test as a subprocess, actually reads/writes real files, sandboxed with a path-traversal guard, and demonstrates retry-with-backoff on a transient failure
- [ ] Structured tracing (`trace.jsonl`) exists and was used to catch at least one real case of the model misrepresenting what it did
- [ ] You can explain: agent loop vs single tool call, short-term vs long-term memory, and guardrails vs retries — each as a one-sentence distinction with a concrete example
- [ ] Slide: "Agent loop architecture" with your own diagram of the plan→act→observe→repeat cycle plus where guardrails and retries each sit in it

---

## Dependencies

- Phase 3: tool/function calling mechanics (schema, structured `tool_calls`, validate-before-execute) — this phase's loop reuses that exact plumbing

## Estimated Effort

6–8 hours (engine + four demos, several hours of which is live iteration against a real local model to find and document actual failure modes rather than assumed ones)

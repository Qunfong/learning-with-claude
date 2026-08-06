## Context

Phase0-3 each shipped as its own Maven module (own pom, own README, one lesson). Phase3 ended with a working hand-rolled tool-loop and named phase4 as "extend to a real agent-loop." The master curriculum (`AI_Learning/ai-coding-learning-plan.md`) scopes Phase4 precisely: agent loop, memory (short vs long-term), error handling/retries/guardrails/cost-latency, with a mini coding-agent (read/edit files, run tests) as the build. Phase5 (skills) and Phase6 (MCP), both already spec'd at `AI_Learning/openspec/specs/`, explicitly build their own demos on top of "the Phase 4 agent" — so whatever `AgentLoop` this phase produces has to be genuinely reusable, not a throwaway.

An earlier pass of this change scoped multi-agent orchestration (sequential vs supervisor patterns, PM/Architect/Developer/Designer roles on a gallery-app task) directly into phase4-agents. That's real, useful design work, but it duplicates Phase7's already-written spec and doesn't match Phase4's actual build target — this design supersedes that pass and rescopes to single-agent only.

## Goals / Non-Goals

**Goals:**
- Show the naive agent loop failing before showing the guardrailed version — risk has to be felt, not just described.
- Make short-term vs long-term memory a felt distinction (survives process restart or not), not a slide.
- Land on the master plan's actual Phase4 build target: a mini coding agent that reads/edits files and runs tests, bounded by the same guardrails demonstrated earlier.
- Cover retry-with-backoff, the one master-plan Phase4 bullet ("error handling, retries") no prior phase touched — phase1's README explicitly deferred it.
- Keep every demo runnable and inspectable standalone (console output), consistent with phase0-3.
- Keep engine code shared (one `AgentLoop`) so role/task differentiation reads as config, reinforcing phase1's strategy-pattern lesson.

**Non-Goals:**
- Not building multi-agent orchestration here — that's Phase7's scope, already spec'd, and it explicitly depends on this phase's `AgentLoop`. No PM/Architect/Developer/Designer roles, no sequential-vs-supervisor comparison, in this change.
- Not building a working gallery app or any other multi-file real-world application — the capstone's scratch Java file is small and self-contained, existing only to give `read_file`/`write_file`/`run_tests` something real to operate on.
- Not introducing an agent framework (LangChain-style). Same discipline as phase0-3: raw `HttpClient` + Jackson.
- Not building a general-purpose memory system (no vector DB, no embeddings/RAG — that's Phase2's territory).

## Decisions

**One module, multiple `main` classes (not one module per concept).**
Phase0-3 pattern is one-module-per-lesson. At 4 concepts (loop, guardrails, memory, capstone) sharing one `AgentLoop` engine, one-module-per-concept would multiply pom/README boilerplate for demos that are fundamentally "same engine, different config" — the scaffolding cost stops paying for itself, and that shared-engine point is itself part of the lesson.

**The naive demo's "failure mode" is documented from a real run, not a hypothesis.**
Live-verified against `llama3.2:3b`: given an unsatisfiable goal ("check inbox until unread=0" with no way to mark messages read) and no guardrails, the model batched 8 identical `check_inbox` calls in a single turn, then falsely declared `"Klaar."` (done) despite the condition never being met. This is used as the documented failure mode instead of a predicted "infinite hang," because it's what actually happened and it's arguably a more dangerous failure (confidently wrong beats hanging forever) — consistent with phase3's own "confidently wrong is worse than a crash" lesson.

**`AgentLoop` has an internal `RUNAWAY_SAFETY_CEILING` (25 iterations) that is explicitly NOT a guardrail.**
A genuinely unbounded loop (`Guardrails.none()`) against a live local model could in principle run far longer than is reasonable for a tutorial to sit through. The ceiling is framed in code comments and console output as infra self-protection for the demo runner, distinct from the `Guardrails` record's user-configured bounds — so the pedagogical point ("no guardrail was configured") stays true even though the process itself doesn't run forever.

**The capstone is a real file-editing, test-running loop, not another console-only text demo.**
Phase4's master-plan build target explicitly is "coding agent that can read/edit files and run tests." Earlier demos in this phase stay console-only (consistent with phase0-3), but the capstone's whole point is to be the artifact Phase5/6 build on, so it has to actually touch files and actually run a test command — scoped to its own small `workspace/` scratch directory so it can't affect anything outside itself.

**Retry-with-backoff lives in `OllamaClient`, demonstrated via the capstone's `run_tests` tool.**
The master plan lists "retries" alongside guardrails as a Phase4 concern. `OllamaClient.chat()` gets a small retry-with-backoff wrapper for transient HTTP failures (timeouts, 5xx) — the same gap phase1's README named and deliberately left open. The capstone's `run_tests` tool simulates one transient failure so the retry path is actually exercised and visible in a run, not just present in code nobody triggers.

**Long-term memory is a flat JSON file behind a `remember`/`recall` tool pair, not a database.**
Consistent with phase0-3's "bewuste vereenvoudigingen" — the point is the *mechanism* (persistence is a tool call, not magic), not the storage engine.

## Risks / Trade-offs

- [`RUNAWAY_SAFETY_CEILING` could be mistaken by a reader for a guardrail, muddying the "no guardrails configured" point of `AgentLoopDemo`] → Mitigation: distinct log-line prefix (`[noodstop demo-runner]` vs `[guardrail]`) and explicit comment distinguishing the two; called out again in the README.
- [Small local model behavior (batch-then-lie) is specific to `llama3.2:3b` and may not reproduce identically on other models] → Mitigation: README documents this as an observed, live-verified run rather than a guaranteed behavior, and the Experimenteer section invites trying a stronger model to compare.
- [Capstone demo writes real files — risk of leaving the repo in a modified state between runs] → Mitigation: scratch files live under `phase4-agents/workspace/` only, are never outside the module, and the README documents how to reset them for a clean rerun (same pattern phase3 already uses for its in-memory data).
- [Model's final natural-language answer can misrepresent what actually happened — live-verified: the model sometimes claims a fix succeeded via `write_file` when no such tool call was ever issued] → Mitigation: `AgentLoop` logs every real tool invocation to a structured `trace.jsonl`, independent of the model's own summary, so ground truth is always verifiable against the log and the actual file state, not just the model's claim.
- [Retry-with-backoff needs a reliably reproducible transient failure to demonstrate, but real network failures aren't controllable on demand] → Mitigation: the capstone's `run_tests` tool simulates exactly one transient failure (e.g. fails the first call, succeeds after), independent of real Ollama/network state, so the retry path is deterministic to observe.

## Migration Plan

Purely additive — new module, no existing code touched. Build order:
1. Scaffold `phase4-agents` module + shared `AgentLoop`/`Tool`/`Guardrails`/`OllamaClient` engine. *(done — engine compiles, `AgentLoopDemo` live-verified.)*
2. `GuardrailsDemo` — each guardrail demonstrated in isolation, then combined.
3. `MemoryDemo` — remember/recall backed by a persisted store, proven across two separate process runs.
4. `OllamaClient` retry-with-backoff.
5. `CodingAgentDemo` (capstone) — read_file/write_file/run_tests tools, small scratch Java file with a failing test, agent loop fixes it.
6. `phase4-agents/README.md` per phase0-3 convention.
7. `AI_Learning/openspec/specs/phase4-agents/spec.md` — the missing sibling spec, written to match phase5/6/7/8's narrative format.

No rollback concerns — no shared/production system affected.

## Open Questions

- Target model: reuse `llama3.2:3b` throughout (used for all live verification so far), or note where a stronger model would behave differently (already relevant for the naive-loop demo's batch-then-lie behavior)?
- Should the capstone's scratch Java file/test be checked into the repo in its "broken" starting state (so a fresh clone can run the demo immediately), or generated by the demo itself on first run? Leaning toward checked-in for reproducibility, matching phase3's `data/orders.json`-style approach used elsewhere in this curriculum.

## Why

Phase3's own README named the gap: "de tool-loop uitbreiden naar een echte agent-loop (plan → act → observe → herhaal)". The master curriculum (`AI_Learning/ai-coding-learning-plan.md`) scopes that gap precisely: Phase 4 = agent loop + memory (short-term vs long-term) + error handling/retries/guardrails/cost-latency tradeoffs, with a mini Claude-Code-style coding agent (reads/edits files, runs tests) as the build target. Phase5 (skills) and Phase6 (MCP) both explicitly build on top of "the Phase 4 agent," and Phase7 (multi-agent/A2A, already spec'd separately) explicitly depends on Phase4+5+6 together — so Phase4 has to stay single-agent and has to produce a reusable agent loop, not a one-off demo.

An earlier pass of this change scoped in multi-agent orchestration (sequential pipeline vs supervisor/router) directly into phase4-agents. That work is real and worth keeping, but it duplicates ground Phase7's spec already owns and doesn't match Phase4's actual build target. This revision rescopes phase4-agents to single-agent only, matching the master plan.

## What Changes

- New Maven module `phase4-agents`, sibling to phase0-3, following the same discipline (hand-rolled `HttpClient` + Jackson against Ollama, no agent framework) but as **one module with multiple `main` classes** (switched via `-Dexec.mainClass`) rather than one module per concept.
- Generic `AgentLoop` engine configured per role via system-prompt + tool subset + `Guardrails`, reused unchanged across every demo (same strategy-pattern lesson as phase1's `ModelClient`, applied to agent behavior instead of backend choice).
- Demo sequence:
  1. `AgentLoopDemo` — naive plan→act→observe→repeat, **no guardrails**, shows a real (live-verified) failure mode: the model batches repeated identical tool calls, then falsely claims completion. Short-term memory (the in-run message list) named explicitly.
  2. `GuardrailsDemo` — same loop, max-iteration cap, token/cost budget, loop-detection, confirm-hook for destructive tools — each guardrail demonstrated in isolation, then all combined re-running demo 1's exact scenario to show it now halts cleanly instead of producing a false-positive.
  3. `MemoryDemo` — short-term (in-run) vs long-term memory: a `remember`/`recall` tool pair backed by a persisted JSON file, proven by running the demo twice as two separate processes.
  4. `CodingAgentDemo` (capstone, matches the master plan's Phase4 build target) — a mini Claude-Code-style loop: `read_file`, `write_file`, `run_tests` tools operating on a small scratch Java file with a failing test. The agent reads the failing test, edits the source, reruns tests, repeats until green or guardrail-bounded. Demonstrates retry-with-backoff on a simulated transient tool failure (closes the "Geen retries/backoff" simplification phase1's README flagged as deliberately deferred).
- `OllamaClient` gains retry-with-backoff on transient HTTP failures (the "error handling, retries" bullet from the master plan — not covered by any prior phase).
- All non-capstone demo output is console-only; the capstone is the one demo that legitimately reads/writes real files, scoped to its own small scratch directory.

## Capabilities

### New Capabilities
- `agent-loop-fundamentals`: single-agent plan→act→observe→repeat loop, the guardrails that bound it (max iterations, budget, loop-detection, confirm-hook), and retry-with-backoff for transient tool/network failures.
- `agent-memory`: short-term (in-run message history) vs long-term (persisted, tool-mediated) memory, and the risk of stale/unbounded memory poisoning later runs.
- `coding-agent-capstone`: a file-editing, test-running agent loop (read_file/write_file/run_tests tools) that ties loop+guardrails+retries together into the master plan's actual Phase4 build target, and is the artifact Phase5 (skill injection) and Phase6 (MCP client) build on top of.

### Modified Capabilities
(none — phase0-3 modules are untouched)

## Impact

- New directory: `phase4-agents/` (pom.xml, README.md, `src/main/java/...`), same shape as phase0/1/3.
- New file: `AI_Learning/openspec/specs/phase4-agents/spec.md` — the missing sibling spec alongside phase5/6/7/8, written in their narrative format (What This Phase Is About / Core Concepts / Build Specification / Open Questions / Success Criteria / Dependencies / Estimated Effort), not this repo's spec-driven schema.
- No changes to existing phase0-3 code.
- Requires Ollama locally with a tool-capable model (same prerequisite as phase3); `llama3.2:3b` confirmed available and used for live verification during implementation.
- Multi-agent orchestration (sequential pipeline vs supervisor/router, PM/Architect/Developer/Designer) is explicitly deferred to Phase7, which already has its own spec at `AI_Learning/openspec/specs/phase7-multi-agent/spec.md` and depends on this phase's `AgentLoop`.

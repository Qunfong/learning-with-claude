## 1. Module scaffold + shared engine

- [x] 1.1 Create `phase4-agents/` module (pom.xml mirroring phase3's Ollama + Jackson deps, JDK 17+)
- [x] 1.2 Implement `AgentLoop` engine: plan→act→observe→repeat, configured via system prompt + tool subset + `Guardrails` (no bounds required — `Guardrails.none()` means unbounded)
- [x] 1.3 Implement shared tool-call plumbing (`Tool`, schema definition, structured `tool_calls` parsing, pre-execution validation via `OllamaClient`)

## 2. AgentLoopDemo (naive loop)

- [x] 2.1 Wire `AgentLoop` to a task with an unsatisfiable stop condition (check_inbox scenario)
- [x] 2.2 Confirm loop runs unbounded and the runaway behavior is visible in console output (live-verified: model batches identical calls, then falsely claims completion)
- [x] 2.3 Name short-term memory explicitly in console output (the in-run message list)

## 3. GuardrailsDemo

- [x] 3.1 Add max-iteration cap demo; verify it halts and reports the guardrail message
- [x] 3.2 Add token/cost budget demo; verify halt-before-next-call on breach
- [x] 3.3 Add repeated-identical-tool-call detection demo; verify abort with "loop detected" message
- [x] 3.4 Add confirm-hook demo for a destructive tool; verify execution is blocked without confirmation, allowed with it
- [x] 3.5 Re-run `AgentLoopDemo`'s exact scenario through the fully guardrailed loop; confirm it now halts cleanly instead of reproducing the naive demo's failure

## 4. MemoryDemo

- [x] 4.1 Implement `remember`/`recall` tools backed by a flat JSON file store, scoped to this demo's own namespace
- [x] 4.2 Verify a fact stored via `remember` in one process run is retrievable via `recall` in a separate, later process run
- [x] 4.3 Demonstrate short-term vs long-term memory side by side in one run's console output
- [x] 4.4 Document the unbounded-growth / stale-memory-poisoning risk and production mitigations in the README

## 5. Retry-with-backoff

- [x] 5.1 Add retry-with-backoff to `OllamaClient.chat()` for transient failures (timeout, 5xx), bounded attempt count (live-verified: caught a real `HttpTimeoutException` during testing, and CodingAgentDemo's `run_tests` reuses the same `Retry` utility)
- [x] 5.2 Verify retries-exhausted still surfaces a clear failure rather than retrying forever (verified against an unreachable port: 3 attempts, exponential backoff, clear `RuntimeException` after ~1.3s)

## 6. CodingAgentDemo (capstone)

- [x] 6.1 Scratch directory is `phase4-agents/workspace/` (not `capstone/` as originally planned — reconciled with a concurrently-developed version of this file) with `Calculator.java` (checked-in, starting bug: `subtract()` does `a+b`) + `CalculatorTest.java`
- [x] 6.2 Implement `read_file`/`write_file` tools scoped to `workspace/` via `resolveSafe` path-traversal guard
- [x] 6.3 Implement `run_tests` tool that actually compiles/runs via `javac`/`java` subprocess and reports pass/fail + detail; simulates exactly one transient failure per run via `Retry.withBackoff`, live-verified
- [x] 6.4 Run the agent loop end-to-end — live-verified across multiple runs: real bug-fix attempts fail in three distinct, now-documented ways (corrupted `write_file` content caught by compile-fail; model fabricating a fake tool call in plain text instead of really calling `write_file`, caught by `trace.jsonl` vs. the file on disk); guardrails/sandbox bound every case
- [x] 6.5 README documents resetting `workspace/Calculator.java` to its starting buggy state

## 7. Documentation

- [x] 7.1 Write `phase4-agents/README.md` following phase0-3 convention, covering all four demos and how to run each via `-Dexec.mainClass`
- [x] 7.2 Cross-check README's Key Learnings against each spec's scenarios (agent-loop-fundamentals, agent-memory, coding-agent-capstone) — every requirement has a corresponding, demonstrable, live-verified learning point
- [x] 7.3 Write the missing `AI_Learning/openspec/specs/phase4-agents/spec.md`, matching the narrative format already used by phase5/6/7/8

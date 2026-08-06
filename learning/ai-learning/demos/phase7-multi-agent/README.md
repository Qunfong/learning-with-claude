# Phase 7 — Multi-Agent & A2A

Two specialist agents (CoderAgent, ReviewerAgent) coordinated by a thin
OrchestratorAgent to ship one small feature. Full spec:
`AI_Learning/openspec/specs/phase7-multi-agent/spec.md`.

This module is **fully self-contained** — its own `pom.xml`, no dependency on
`phase4-agents`/`phase5-skills`/`phase6-mcp` as Maven modules. The handful of
engine classes it needs from Phase 4 (`OllamaClient`, `Retry`, `Tool`,
`Guardrails`, `AgentLoop`) are copied in verbatim (same reasoning phase5/phase6
already use: each phase demo must stand alone).

## How to run

Prerequisites: [Ollama](https://ollama.com) running locally with
`qwen2.5-coder:7b` and `llama3.2:3b` pulled (`ollama pull <model>`).

```bash
cd demos/phase7-multi-agent
mvn -q compile exec:java
```

### Fallback / mock mode

If Ollama isn't running (or you just want to see the message flow without
waiting on live inference), pass `-Dphase7.mock=true`:

```bash
mvn -q compile exec:java -Dphase7.mock=true
```

In mock mode, `CoderAgent` and `ReviewerAgent` skip the real HTTP call to
Ollama and return canned-but-realistic responses (a real exponential-backoff
implementation as the "generated" code, "No violations found" as the LLM
verdict) — the full Orchestrator → Coder → Reviewer flow, artifact handoff,
and `TaskResult` shape are exercised identically to a live run. This is the
"clear fallback so it still compiles and runs" the build asked for; it was
**not** needed in this environment (Ollama was reachable with both models),
but is there so the demo is not hostage to whether a model happens to be
pulled on whatever machine runs it next. `main()` also catches any exception
from a live run (Ollama unreachable, model missing) and prints the exact fix
+ the mock-mode command instead of a raw stack trace.

## What actually happened, live (not mocked)

One real end-to-end run, `qwen2.5-coder:7b` generating and `llama3.2:3b`
reviewing:

- CoderAgent rewrote `context/OllamaClient.java`'s `complete()` with a real
  10-attempt exponential-backoff loop (`initialDelay.multipliedBy(2).min(maxDelay)`,
  capped at 8s) — a correct, working implementation of the ticket.
- The **static** reviewer pass caught two real, deterministic issues the LLM
  pass also independently found variants of:
  - `R9`: the rewritten `complete()` body is 31 lines (over the ~20-line
    guideline) — true, and a legitimate "extract a helper" prompt.
  - `R6`: the generated code has an **unreachable `return null;`** after the
    retry loop (the loop always either `return`s or `throw`s on the last
    attempt) — a real, if harmless, dead-code smell a human reviewer would
    also flag.
  - The LLM pass, using the java-standards skill as its system prompt, added
    R1 (catches `IOException | InterruptedException` together, doesn't
    preserve the original exception distinctly), R2, R3, R4 (`10` and delay
    values aren't named constants), R5, and R7 — a broader, fuzzier set that
    a regex scanner structurally cannot produce (see "static vs. LLM" below).

This is a genuine multi-agent win: the static pass is 100% reproducible and
catches structural issues instantly; the LLM pass caught real *semantic*
sloppiness (magic numbers, non-preserved exception cause) the regex scanner
has no way to evaluate. Neither pass alone would have produced this feedback.

## Message flow

```
User ticket: "Add exponential backoff retry to OllamaClient.complete()"
        |
        v
+----------------------+
|  OrchestratorAgent    |  1. reads context/OllamaClient.java (MCP-style read,
+----------+-----------+     see "MCP simulation" below)
           |
           | Task(type=code.generate,
           |      input={featureDescription, existingCode, fileName})
           v
     +-----------+
     | CoderAgent|  2. AgentLoop (Phase 4 engine) -> qwen2.5-coder:7b
     +-----+-----+  3. returns TaskResult{DONE, artifacts=[java_file]}
           |
           | Task(type=code.review, input={code=<CoderAgent's artifact>})
           v
    +--------------+
    | ReviewerAgent |  4. static rule scan (subset of java-standards R1-R10)
    +------+-------+  5. LLM review (skill.md as system prompt) -> llama3.2:3b
           |           6. returns TaskResult{DONE,
           |                artifacts=[java_file_annotated], issues=[...]}
           v
+----------------------+
|  OrchestratorAgent    |  7. presents final annotated code + issues list
+----------------------+
```

Draw this from memory per the spec's success criteria — it is intentionally
the "ORCHESTRATOR (hierarchical)" topology from spec.md, run **sequentially**
(Reviewer structurally depends on Coder's output, so there is nothing to
parallelize here — see Open Question #2 below).

## Design notes / where this deviates from spec.md

1. **Ticket target file.** spec.md's ticket says
   `OllamaClient.complete()` — but the real `phase4-agents/OllamaClient.java`
   has a method called `chat()`, and it *already* has retry (via
   `Retry.withBackoff`, added by Phase 4 itself). Modifying it again would be
   redundant and confusing. Instead, `context/OllamaClient.java` in this
   module is a deliberate **pre-Phase-4 stand-in**: a bare `HttpClient`
   wrapper with a `complete()` method and *no* retry — literally the code the
   ticket describes fixing. It is data for the demo (read by
   `OrchestratorAgent`, analogous to `phase4-agents/workspace/Calculator.java`
   being data for `CodingAgentDemo`), not compiled — it lives outside
   `src/main/java`. This is the single most notable deviation from the spec
   text; everything else follows the spec directly.

2. **MCP read is simulated, not a real MCP server call.** spec.md says
   "Orchestrator reads `OllamaClient.java` via MCP (Phase 6)". Standing up
   the real `phase6-mcp` server for this would require importing that
   module's Spring Boot REST backend (`CodeAnalysisApplication`, port 8080)
   *and* the MCP stdio server as a subprocess — real cross-module coupling,
   exactly what "independent Maven module" rules out. `McpStyleFileReader`
   builds the identical JSON-RPC **response shape** (`uri` / `mimeType` /
   `text`, see `phase6-mcp/McpServer#resourcesRead`) from a direct local file
   read. The protocol shape is visible in the code and in the printed output
   (`"reading ... (MCP-style resources/read)"`); the transport underneath is
   not the real thing. Swapping in a real `McpClient` (already written in
   `phase4-agents/McpClient.java`) would be a localized change to one method.

3. **CoderAgent's `AgentLoop` runs with an empty tool set.** The spec asks
   CoderAgent to "call the Phase 4 agent loop" — it does, with the exact same
   `AgentLoop`/`Guardrails`/`Retry`/`OllamaClient` classes, just configured
   with zero tools. "Rewrite this file for this feature" is a single
   generate-and-return step, not a multi-step tool-use task, so the loop
   runs exactly one iteration by design. `Tool.java` is still copied in
   (unused) so the constructor signature matches Phase 4 exactly, in case you
   want to extend this (see Experiment ideas below).

4. **Static rule coverage is a deliberate subset.** Of java-standards'
   R1-R10, the static scanner in `ReviewerAgent` reliably checks **R1, R2,
   R6, R8, R9** — these are syntactically detectable with a small regex/line
   scanner. **R3 (immutability), R4 (magic values), R5 (naming quality), R7
   (precondition validation), R10 (test naming)** need real semantic
   judgment (is this actually a "meaningful" literal? is this name
   "intention-revealing"?) that a regex cannot make reliably — those are left
   to the LLM pass entirely, and the live run above shows the LLM pass
   catching several of them.

## Open Questions to Explore — answers/notes

**1. What happens when CoderAgent and ReviewerAgent disagree? Who arbitrates?**
They can't really "disagree" in this build — Reviewer only ever *comments on*
Coder's artifact, it never rewrites it or blocks the pipeline. Nothing
arbitrates; `OrchestratorAgent` presents both artifact and issues list
unconditionally, and a human is the arbiter reading the final output. A
sturdier design (not built here, worth trying as an exercise) would treat
"disagreement" as ReviewerAgent returning `issues` non-empty and
Orchestrator running a **second round**: re-delegate to CoderAgent with the
issues list as new input, cap total rounds with a guardrail (same
`maxIterations` concept `AgentLoop` already has, one level up), and surface
"still has issues after N rounds" rather than silently accepting round 1.
That is a real design decision, not a detail — it turns this from a
"one-shot pipeline" into a genuine iterative multi-agent loop, at the cost of
more Ollama calls (see Q2).

**2. How many agent hops before latency + cost make it not worth it?**
This demo did 2 LLM calls (CoderAgent generate, ReviewerAgent's LLM pass) for
one feature. The live run's CoderAgent call alone used 1133 cumulative
tokens for one iteration. Each additional hop is a full model round-trip —
locally that's seconds; against a hosted API it's both latency *and* $/token.
Practical rule of thumb: hops are worth it while each one adds
**non-redundant judgment** (Coder writes code, Reviewer checks it against
rules Coder wasn't told to self-check) — a third agent that just rephrases
the second agent's output adds cost with no new judgment. The moment you're
adding hops "to be thorough" rather than because a *different* specialist
skill is genuinely needed, you've crossed from architecture into padding.

**3. Agent discovery: how does Orchestrator know ReviewerAgent exists?**
In this build: compile-time — `OrchestratorAgent`'s constructor takes both
`A2AAgent` references directly (see `OrchestratorDemo.main`). That's the
simplest possible answer and it's honest about Option B's scope (in-process,
no registry needed because there's nothing to discover across a process
boundary). Real A2A's answer is Agent Cards served from a well-known URL —
a runtime service registry an orchestrator queries/watches. The two are the
same *concept* (an orchestrator needs a name → capability → "how do I reach
it" mapping) at different points in the lifecycle: compile-time and static
vs. runtime and dynamic. The jump from this demo to Phase 8's real HTTP A2A
is exactly this: `AgentCard.endpoint()` stops being a decorative string and
becomes a URL you actually POST to, resolved via a registry instead of a
constructor argument.

**4. Shared state: if Coder and Reviewer need the same file, who owns it?**
Neither agent touches disk in this build — `OrchestratorAgent` reads the file
once and passes its *content* through `Task.input()`; CoderAgent returns new
content as an `Artifact`, Reviewer never sees the original file at all, only
Coder's artifact. So there's no shared mutable state, by construction — the
file is read once, upstream, and everything downstream is pure
data-in/data-out through Task/TaskResult. That sidesteps the question rather
than answering it: a design where *both* agents legitimately need to read
(or worse, write) the same file concurrently needs an explicit owner —
typically the Orchestrator, which serializes access, exactly like a
transaction coordinator. `phase4-agents`' `CodingAgentDemo` sandbox
(`resolveSafe`, confirm-hook on `write_file`) is the relevant prior art here:
whoever can write must be the sole writer, or you need real locking.

**5. Failure modes: CoderAgent times out. Does Orchestrator retry, skip, or fail?**
Currently: **fail**. `OrchestratorAgent.handle` checks
`codeResult.state() != TaskState.DONE` and immediately returns
`TaskResult.failed(...)` — no retry, no fallback path, no partial result.
This mirrors `AgentLoop`'s own philosophy (Phase 4 README): retries belong at
a single call boundary (`OllamaClient.chat` already retries transient HTTP
failures via `Retry.withBackoff`) — but a *guardrail tripping*
(`maxIterations`, `AgentLoop` returning `null`) is a deliberate stop, not a
transient fault, and re-trying it blindly would just reproduce the same
failure. The honest answer for "should Orchestrator retry a failed
CoderAgent task" is: retry only if you can point to what would be different
on attempt 2 (different model? relaxed guardrail? a hint from the previous
failure?) — retrying identical input against a deterministic-ish pipeline
just wastes a second full LLM round-trip for the same result.

## Success criteria — self-check

- [x] CoderAgent generates Java code from a feature description — live run
      produced a working exponential-backoff `complete()`.
- [x] ReviewerAgent reviews against java-standards and returns structured
      feedback — static (`R6`, `R9`) + LLM (`R1`-`R7`) issues, both present
      in the live run above.
- [x] Orchestrator coordinates the full flow end-to-end — `mvn -q compile
      exec:java` runs the whole pipeline in one process.
- [x] Retry scenario (ticket above) completes successfully — see "What
      actually happened, live" above.
- [ ] "Draw the message flow diagram from memory" / "explain orchestrator vs.
      peer-to-peer" / "slide" — these are exercises for you, the diagram
      above and the topology comparison in spec.md are the study material,
      not something a demo can tick off for you.

## Experiment ideas

- Turn CoderAgent's empty tool set into a real one: add a `validate_syntax`
  tool (brace-balance check, or shell out to `javac -Xlint`) and a
  `Guardrails(3, 0, true, confirmHook)` loop that actually iterates on
  compile failure — then compare reliability against `CodingAgentDemo`'s
  documented small-model tool-calling failure modes (Phase 4 README).
- Implement the "disagreement" loop from Open Question #1: re-delegate to
  CoderAgent with ReviewerAgent's `issues` list as new input, capped at N
  rounds.
- Swap `McpStyleFileReader` for a real `phase4-agents/McpClient.java` talking
  to a running `phase6-mcp` `McpServer` subprocess — the point in the code
  where Option B's simulation would become the real thing.
- Add a third agent (e.g. `TestWriterAgent`) and observe at what point Open
  Question #2's "hops aren't worth it" threshold starts to bite in wall-clock
  terms on your machine.

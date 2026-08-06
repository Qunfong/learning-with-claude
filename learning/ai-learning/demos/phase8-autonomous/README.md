# Phase 8 — Demo: Autonomous Pipeline (checkpoints, gates, observability, safety rails)

Goal: phases 4-7 built capable agents. Phase 8 makes one run **unattended** —
with checkpointing, observability, human gates, and safety rails — while
pausing at defined points for a human decision. See
`AI_Learning/openspec/specs/phase8-autonomous/spec.md` for the full
phase specification (state machine, checkpoint format, safety rails, demo
ticket, success criteria).

This is a fully independent Maven module (own `pom.xml`, flat class
structure), same convention as every other phase — **no imports from
`demos/phase7-multi-agent/`**, which is a separate module built concurrently.
`CoderAgent`/`ReviewerAgent` here are self-contained reimplementations, kept
conceptually close to phase7's `A2AAgent` interface (see `A2A.java`).

## Architecture: one state machine, eight components

| Class | Role |
|---|---|
| `Ticket` | input record: `id, title, description, targetFile` (exactly per spec) |
| `PlannerAgent` | PLANNING: reads the target file "via MCP" (see `LocalMcpFileServer`), asks the model for a short plan |
| `CoderAgent` | CODING: `A2AAgent`, capability `code.generate` — returns the complete new file content |
| `ReviewerAgent` | runs before Gate 2: `A2AAgent`, capability `code.review` — java-standards skill + deterministic lint checks |
| `TestRunner` | TESTING: runs real `mvn test` against `fixture/`, captures output; supports `--chaos-fail=N` for deterministic ESCALATE demos |
| `Gate` | GATE 1 / GATE 2: blocks on stdin `y`/`n` + optional feedback, or auto-approves with `--auto` |
| `CheckpointStore` | append-only JSONL writer → `run-{id}.jsonl` |
| `ObservabilityCollector` | tokens/latency/cost/tests/gates per run → `traces/run-{id}.jsonl` + end-of-run summary |
| `GitOps` / `PROpener` | safety-rail stash + PR OPEN (git branch/commit/`gh pr create`) — gracefully **simulates** when not in a git repo |
| `AutonomousPipeline` | the state machine itself (`main`) |

Supporting/reused-pattern classes: `OllamaClient` (single-shot `/api/chat`
wrapper), `Retry` (backoff for that HTTP call — **not** the pipeline's own
RETRY state, see `Retry.java`'s header comment), `LocalMcpFileServer`
(in-process MCP-shaped file reader), `A2A.java` (`AgentCard`/`Task`/`TaskResult`/`A2AAgent`).

## State machine (exactly per spec)

```
         ticket arrives
               │
               ▼
         ┌───────────┐
    ┌───▶│  PLANNING  │  PlannerAgent reads target file + proposes a plan
    │    └─────┬──────┘
    │           │
    │     ┌─────▼──────────────┐
    │     │  GATE 1 (human)    │  "Does this plan look right?"
    │     └─────┬──────────────┘
    │           │ approved
    │           ▼
    │     ┌───────────┐
    │     │  CODING   │  CoderAgent writes the file, ReviewerAgent reviews it
    │     └─────┬─────┘
    │           │
    │     ┌─────▼─────┐
    │     │  TESTING  │  mvn test against fixture/, real subprocess
    │     └─────┬─────┘
    │           │ tests fail?
    │           ├──────────────────────────────────┐
    │           │ pass                             │ fail (≤3 retries)
    │           ▼                                  ▼
    │     ┌─────────────────┐              ┌─────────────┐
    │     │  GATE 2 (human) │              │   RETRY     │──▶ back to CODING
    │     │  "Open PR?"     │              └──────┬──────┘
    │     └─────┬───────────┘                     │ 3rd fail
    │           │ approved                         ▼
    │           ▼                           ┌────────────┐
    │     ┌───────────┐                     │  ESCALATE  │ → notify human, stop
    │     │  PR OPEN  │                     └────────────┘
    │     └───────────┘
    │
    └─ if rejected at GATE 1 or 2: back to PLANNING with feedback
```

"Slide" version (control points, one line each):
**PLAN → [human: right plan?] → CODE+REVIEW → TEST → [human: ship it?] → PR**,
with a **RETRY×3 → ESCALATE** safety valve on the TEST edge and a
**reject → back to PLAN with feedback** edge on both gates.

## Running it

Run from **this** directory (`demos/phase8-autonomous/`) — same convention as
every sibling phase.

```bash
# 0. one-time: warm the fixture's Maven cache (JUnit + slf4j deps)
cd fixture && mvn -q -B test-compile ; cd ..

# 1. compile
mvn -q compile

# 2. interactive run (real stdin y/n prompts at Gate 1 and Gate 2)
mvn -q compile exec:java

# 3. non-interactive run (both gates auto-approved) -- for scripting/CI/testing
mvn -q compile exec:java -Dexec.args="--auto"

# 4. force the RETRY x3 -> ESCALATE path deterministically (doesn't depend on
#    the LLM's code-gen quality being bad on a given run)
mvn -q compile exec:java -Dexec.args="--auto --chaos-fail=3"

# 5. drive gates via piped stdin instead of a flag (also works):
#    each line is one gate answer; a rejection consumes an extra line for feedback
printf 'n\nsome feedback\ny\ny\n' | mvn -q compile exec:java
```

CLI flags (all optional):
| Flag | Effect |
|---|---|
| `--auto` | auto-approve both gates (the "non-interactive flag for testing" — no stdin needed) |
| `--chaos-fail=N` | force the first N `TestRunner` verdicts to FAIL regardless of the real `mvn test` result (see "Chaos injection" below) |
| `--token-budget=N` | override the default token budget (40000) — set it low (e.g. `50`) to see the budget-exceeded safety rail trip instead |
| `--i-understand-the-risk` | required alongside `--auto` if this directory is EVER a real git repository (see below) |

**`--auto` safety interlock:** `AutonomousPipeline` checks `GitOps.isGitRepo()`
before honoring `--auto`. In THIS environment that's always false (confirmed
not a git repo), so `--auto` alone works as shown above. If this directory is
ever put under version control, `--auto` on its own will refuse to run
(exit 1) — because at that point Gate 1/Gate 2 stop being harmless prints and
start gating a REAL `git commit` / `gh pr create`, and auto-approving those
without a human is exactly the "fully auto, no gates" corner of the autonomy
spectrum the spec explicitly contrasts with this phase's "supervised
autonomy". `--auto --i-understand-the-risk` overrides it explicitly.

Requires Ollama running locally with `qwen2.5-coder:7b` (CoderAgent) and
`llama3.2:3b` (PlannerAgent/ReviewerAgent) pulled — same models
`phase4-agents`/`phase5-skills` already use.

### Resetting the fixture between runs

Each run rewrites `fixture/src/main/java/fixture/FixtureOllamaClient.java` (a
real file write, sandboxed to this module's directory — see
`LocalMcpFileServer`). To repeat the demo from the original buggy state:

```java
public String complete(HttpCall httpCall) throws Exception {
    int status = httpCall.call();
    if (status >= 500) {
        throw new RuntimeException("HTTP " + status);
    }
    return "ok";
}
```
(no imports beyond `package fixture;` — this is the entire original file, no
`Logger` field, no retry loop.)

## Checkpoint & trace files

- **Checkpoints** (append-only JSONL, one event per line): `run-{id}.jsonl`
  in this directory (one file per run; regenerated on every run, so only the
  most recent run's checkpoint file is normally present here at any given
  time — that's expected, not a bug).
- **Traces** (observability: tokens/latency/cost/tests/gates):
  `traces/run-{id}.jsonl`, one JSON object per step/event, plus a final
  `run_summary` line. All six real runs described below (see "What actually
  happened") have their trace file committed in `traces/` as evidence, even
  where the matching root-level checkpoint file was superseded by a later run.
- End-of-run summary is also printed to stdout (tokens, hosted-equivalent
  cost, duration, files written, test runs, gate decisions, outcome).

Example checkpoint line (real, from a committed run):
```json
{"event":"gate1_rejected","ts":"2026-07-28T22:04:31.614Z","by":"human","feedback":"please use a while-loop retry helper instead, and mention units in the log message"}
```

## Safety rails implemented

| Rail | Where |
|---|---|
| Max step count (20) | `AutonomousPipeline`'s `stepCount` check at the top of the loop |
| Max token budget per run (alert + stop) | `ObservabilityCollector.recordStep` returns `true` when the cumulative budget is exceeded; pipeline routes to ESCALATE |
| Tests must pass before Gate 2 | structural: `GATE2` is only reachable from `TESTING`'s pass branch |
| Unreviewed changes | `ReviewerAgent` always runs between CODING's write and TESTING |
| Rollback needed | `GitOps.stashIfPossible()` before the first write each run; `restoreStashIfNeeded()` on ESCALATE/max-step-abort |
| File system damage | `LocalMcpFileServer` sandboxes every read/write to this module's root (path-traversal guard, same pattern as `phase4-agents/CodingAgentDemo.resolveSafe`) |

### Chaos injection (`--chaos-fail=N`)

`TestRunner` always runs the **real** `mvn test` (genuine duration, genuine
output) but can override the pass/fail *verdict* for the first N calls,
clearly annotated in the captured output (`[chaos-fail] verdict forced to
FAIL...`) so it's never mistaken for a real result. Same "simulate exactly
one deterministic failure to exercise a code path" pattern as
`phase4-agents/CodingAgentDemo`'s `TRANSIENT_ALREADY_SIMULATED`. This exists
because the ESCALATE path shouldn't require gambling on the LLM writing bad
code on a given run to demonstrate — see "What actually happened" below for
why that gamble turned out unnecessary anyway.

## Deviations from spec

**Ticket target file substituted.** The spec's literal demo ticket targets
`demos/phase1-local-serving/src/main/java/LocalVsHostedDemo.java`. That file
**does exist** (confirmed before building), but:
- `phase1-local-serving`'s `pom.xml` has no JUnit dependency and no
  `src/test/`, so `mvn test` there is a silent no-op — it gives the pipeline
  no real pass/fail signal to react to, which defeats the entire point of
  the TESTING state and the RETRY/ESCALATE logic.
- Running `mvn test` against a *sibling* module's directory from inside this
  module, while technically possible via `-f ../phase1-local-serving/pom.xml`,
  would blur the "each phase is an independent module" boundary and risk
  mutating another phase's demo file as a side effect of this one.

Instead, `fixture/` is a small **standalone** Maven module (own `pom.xml`,
JUnit 5 + slf4j) living inside `phase8-autonomous/` but not a submodule of it
(Maven never recurses into it automatically — confirmed `mvn compile` at the
parent root does not touch it). It reproduces the **same scenario** as the
ticket — `OllamaClient.complete()` needs retry-with-backoff on HTTP 5xx — as
`FixtureOllamaClient.complete()`, with a real test
(`FixtureOllamaClientTest`) that **fails against the original buggy code**
and only passes once retry logic is correctly added. `Ticket.targetFile()`
points at `fixture/src/main/java/fixture/FixtureOllamaClient.java`.

**Git/`gh` handling.** `D:\Users\Qunfo\Downloads\AI_Learning` is confirmed
**not** a git repository (`git status` → `fatal: not a git repository`).
Every git-touching operation (`GitOps.stashIfPossible`, `GitOps.createBranch`,
`GitOps.commit`, `PROpener.openPr`) detects this via
`git rev-parse --is-inside-work-tree` and **simulates** — prints the exact
commands it would have run (`git checkout -b ...`, `git add ...`,
`git commit -m ...`, `gh pr create ...`) — instead of failing. The `gh` CLI
**is** installed in this environment (`gh --version` succeeds), which is
reported separately from the git-repo check, since PR creation needs both a
real repo+remote and `gh`. The real-git code path is fully implemented (not
stubbed), so it will behave correctly the day this project is put under
version control.

**PlannerAgent's "Phase 4 loop"** is a single deterministic file read
(`LocalMcpFileServer`, an in-process MCP-shaped stand-in — see its header
comment for why a real subprocess/stdio MCP server, already covered in
phase6, wasn't re-built here) followed by one reasoning call, rather than a
full multi-turn tool-calling `AgentLoop`. The read doesn't need the model to
decide *when* to call it — there's exactly one, always — so wiring up the
generic tool-calling engine from phase4 for a single always-called tool
would add files and moving parts without adding to what this phase teaches
(orchestration/control, not tool-calling mechanics — that's phase4's job).

**CoderAgent generates the full file as plain completion text**, not via a
`write_file` tool call. `phase4-agents/CodingAgentDemo`'s own README
documents three real runs where a small local model handling a full-file
`write_file` argument either produced mangled escaped content or fabricated
a "did it" narrative without ever calling the tool. Asking for the file
content as the direct chat response (same approach `phase5-skills/SkillsDemo`
uses) sidesteps that specific, previously-documented failure mode.

**ReviewerAgent doesn't hard-block on findings.** The spec's state diagram
has no "review failed" edge — Gate 2 is reached from TESTING's pass branch
regardless of review findings. So `ReviewerAgent` combines deterministic
static checks (regex, 100% reproducible) with an LLM pass using the
java-standards skill as system prompt, and surfaces all findings to the
human at Gate 2 rather than auto-vetoing. See Open Question 3 below for
where a harder gate could go.

## What actually happened (6 real, live runs — Ollama local, `llama3.2:3b` + `qwen2.5-coder:7b`)

All six runs' trace files are committed in `traces/` as evidence
(`traces/run-DEMO-001-<epochMillis>.jsonl`).

1. **Organic ESCALATE (no chaos flag)** — `qwen2.5-coder:7b` added retry logic
   using SLF4J (`org.slf4j.Logger`), which the fixture's `pom.xml` didn't
   depend on at the time. Real `mvn test` failed to *compile* three
   consecutive times (`package org.slf4j does not exist`) → genuine
   RETRY → RETRY → ESCALATE, no synthetic failure involved. Fixed by adding
   `slf4j-api`/`slf4j-simple` to `fixture/pom.xml` (a legitimate fix — SLF4J
   over `System.out` is exactly what the java-standards skill's R8 asks for,
   the fixture was just missing the dependency).
2. **Organic ESCALATE again** — even with SLF4J available, the model
   renamed the public `complete(HttpCall)` method to `executeHttpCall(...)`,
   breaking the test's contract (`cannot find symbol: method complete(...)`)
   three consecutive times. Real, structural evidence for the same lesson
   `phase4-agents/CodingAgentDemo`'s README documents: a coding model doesn't
   reliably preserve an existing public contract unless *explicitly* told
   not to touch it. Fixed by adding an explicit constraint to both the
   ticket description and `CoderAgent`'s system prompt ("keep the existing
   public method name and signature exactly as ...").
3. **Full success, `--auto`** — PLANNING → GATE1 (auto-approved) → CODING →
   TESTING (passed, real `mvn test`, 4.6s) → GATE2 (auto-approved) →
   PR OPEN (simulated, correctly detected non-git-repo). 3 LLM steps, 3595
   total tokens, $0.0200 hosted-equivalent cost, 44.4s wall clock.
4. **Interactive Gate 1 rejection → re-plan → approve → full success** —
   real stdin (`printf 'n\n<feedback>\ny\ny\n' | mvn ... exec:java`, no
   `--auto`). Gate 1 rejected with feedback ("use a while-loop retry helper,
   mention units in the log"), pipeline looped back to PLANNING, the second
   plan's own text explicitly says *"Note: the original `complete` method
   name and signature are preserved to maintain backwards compatibility"* —
   direct evidence the feedback loop reached the model. Reached GATE2,
   approved, PR simulated. `gate decisions: [gate1=rejected, gate1=approved,
   gate2=approved]` in the printed summary confirms it end to end.
5. **`--chaos-fail=3`** — real `mvn test` passed all three times (exit code
   0 every attempt, confirmed in the captured output:
   `real mvn test exit code was 0`), but the verdict was forced to FAIL each
   time, deterministically driving RETRY → RETRY → ESCALATE. Confirms the
   chaos-injection mechanism itself works independently of code quality.
6. **Organic ESCALATE, a third distinct real bug** — a later `--auto` re-run
   (after resetting the fixture) produced a classic off-by-one: the retry
   loop ran `for (retryCount = 0; retryCount <= maxRetries; retryCount++)`
   — 4 attempts, not 3 — so `shouldGiveUp_afterThreeFailedAttempts` failed
   with `expected: <3> but was: <4>` three consecutive times. A third
   genuinely different failure mode than runs 1-2 (missing dependency,
   renamed contract, now an off-by-one), all caught by the same real
   `mvn test` loop.

Across all 6 runs: every checkpoint event in the spec's example format
appeared with the right fields; `ObservabilityCollector`'s printed summary
and `traces/*.jsonl` always agreed with each other; no run exceeded the
40000-token budget (peak was 29% of it, run 6); no run needed more than 7
state-machine steps (max step count of 20 was never close to being tested
by a real run — set `--token-budget` low if you want to see that rail trip
instead, or drop `MAX_STEPS` in `AutonomousPipeline.java` for a quick look).

## Notes on "Open Questions to Explore" (spec section)

1. **Gate UX**: stdin is exactly as crude as the spec says. The
   `--auto`/`--chaos-fail` flags exist here for the same reason a *real*
   system would want a Slack/email/web-UI gate: **testability**. A gate that
   can only be answered by a human typing at a terminal cannot be exercised
   in CI. The natural next step is `Gate` becoming an interface (`ask()`
   returning a `CompletableFuture<Decision>`) with a Slack-bot implementation
   posting a message with Approve/Reject buttons and the pipeline process
   parking on the future — same shape as this demo's blocking call, just a
   different backing implementation.
2. **Parallel coding**: this demo's `CheckpointStore`/`ObservabilityCollector`
   both key off `runId` (ticket ID + timestamp) and write to their own file —
   two tickets running concurrently already get isolated checkpoint/trace
   files "for free". What's **not** isolated: `fixture/` itself (both runs'
   `CoderAgent` would write the same file) and `GitOps`'s stash (one working
   tree, one stash stack). Real parallel tickets need one git worktree/branch
   per ticket, not just a run ID — see `GitOps.createBranch`, which already
   takes a per-ticket branch name; the missing piece is a per-ticket
   *working directory*, not just a branch name.
3. **Eval vs Observability**: `ObservabilityCollector` answers "how much did
   this cost", not "was the code good". `ReviewerAgent`'s deterministic
   static checks (`staticLintChecks`) are the closest thing to an eval here,
   and they're intentionally narrow (3 regex rules). The simplest real eval
   to add next: run `FixtureOllamaClientTest` (or an equivalent) as a
   **held-out** test the ticket never mentions, so passing it is evidence of
   generalization rather than the model pattern-matching the one test it
   was told about.
4. **Cost modeling**: `ObservabilityCollector` uses Claude Sonnet's published
   per-token pricing as a reference rate (this run is local/free) — see its
   header comment. A real threshold would key off ticket *complexity*: this
   demo's DEMO-001 ticket cost 2828-3411 input / 767-1129 output tokens
   end-to-end on a full success run (runs 3-4 above) — a good rough
   "simple ticket" baseline to compare future, more complex tickets against
   when picking `--token-budget`.
5. **The human gates paradox**: run 3 above (`--auto`, both gates
   auto-approved) shows what happens when you *remove* both gates today —
   nothing catches CoderAgent's naming/style choices before they'd ship,
   only `ReviewerAgent`'s advisory findings would be on record. Given runs 1
   and 2's organic escalations (unprompted SLF4J dependency assumption,
   unprompted method rename) both happened *before* any gate was reached,
   removing Gate 1 wouldn't have caught either — they were CODING-stage
   failures, not planning-stage ones. That's a concrete data point for "at
   what maturity do you remove a gate": watch **where** failures actually
   occur across enough runs, and only remove the gate that sits after the
   stage that's stopped failing.

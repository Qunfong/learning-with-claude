# Phase 8 — Autonomous Systems

## What This Phase Is About

Phases 4–7 built capable agents. Phase 8 makes them run unattended — with
checkpointing, observability, human gates, and safety rails.
The capstone build is a pipeline that takes a ticket and opens a PR,
pausing at defined points for human approval.

This phase is about CONTROL, not capability. Your agent can already code.
Now you need to know: what did it do, why, how much did it cost, and
can you roll it back?

---

## Core Concepts

### The Autonomy Spectrum

```
FULLY MANUAL        SUPERVISED          AUTONOMOUS          FULLY AUTO
──────────────────────────────────────────────────────────────────────
Human does          Human approves      Agent runs,         Agent runs,
everything          each step           human gates         no gates

                    ← Phase 4 here →    ← Phase 8 here →

Gates slow you      Gates save you      Right balance depends
down unnecessarily  when agent is wrong on: task risk + agent maturity
```

### State Machine for the Build

```
         ticket arrives
               │
               ▼
         ┌───────────┐
    ┌───▶│  PLANNING  │  agent reads ticket, proposes plan
    │    └─────┬──────┘
    │           │
    │     ┌─────▼──────────────┐
    │     │  GATE 1 (human)    │  "Does this plan look right?"
    │     │  approve / reject  │
    │     └─────┬──────────────┘
    │           │ approved
    │           ▼
    │     ┌───────────┐
    │     │  CODING   │  writes code, runs tests locally
    │     └─────┬─────┘
    │           │
    │     ┌─────▼────────────────────────────────┐
    │     │  TESTING  │  mvn test, capture result │
    │     └─────┬─────┴──────────────────────────┘
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
    │     ┌───────────┐                     │  ESCALATE  │ → notify human
    │     │  PR OPEN  │                     └────────────┘
    │     └───────────┘
    │
    └─ if rejected at GATE 1 or 2: back to PLANNING with feedback
```

### State Persistence (Checkpointing)

Why: agent crashes mid-task → you lose all progress. With checkpoints, resume from last gate.

```java
// Checkpoint format (JSONL — append-only, easy to read/replay)
// run-{id}.jsonl:
{"event":"run_start","ticket":"...","timestamp":"..."}
{"event":"plan_generated","plan":"...","step_tokens":450}
{"event":"gate1_waiting","gate":"plan_approval"}
{"event":"gate1_approved","by":"human","timestamp":"..."}
{"event":"coding_start"}
{"event":"file_written","path":"src/...","chars":1240}
{"event":"test_run","passed":true,"duration_ms":3200}
{"event":"gate2_waiting"}
{"event":"gate2_approved"}
{"event":"pr_opened","url":"...","cost_usd":0.043}
```

### Observability

```
WHAT TO TRACK             WHY IT MATTERS
────────────────────────────────────────────────────────
Token count per step      Know WHERE cost accumulates
Latency per step          Find the slow agents
Files written             Audit trail for safety
Test results              Know when agent is struggling
Gate decisions            Human oversight log
Total cost per run        Budget control

→ Write to: traces/run-{id}.jsonl (structured, queryable)
→ Print summary at end: cost, duration, steps, outcome
```

### Safety Rails

```
RISK                    MITIGATION
──────────────────────────────────────────────────────────────
File system damage      Sandbox: only write inside project dir
Infinite loop           Max step count (default: 20)
Cost explosion          Max token budget per run (alert + stop)
Bad code in PR          Tests must pass before Gate 2
Unreviewed changes      Phase 7 reviewer agent runs before Gate 2
Rollback needed         git stash before any write; restore on abort
```

---

## Build Specification

### Input
A "ticket" struct:
```java
record Ticket(String id, String title, String description, String targetFile) {}
```

### Pipeline Components (reuse from previous phases)

```
AutonomousPipeline
├── PlannerAgent        (Phase 4 loop + Phase 6 MCP to read files)
├── CoderAgent          (Phase 7)
├── ReviewerAgent       (Phase 7 + Phase 5 skill)
├── TestRunner          (runs: mvn test, captures output)
├── Gate               (blocks, waits for stdin: y/n + optional feedback)
├── CheckpointStore    (append-only JSONL writer)
├── ObservabilityCollector (token + cost + latency per step)
└── PROpener           (git branch + commit + gh pr create)
```

### Demo Ticket

```java
new Ticket(
    "DEMO-001",
    "Add retry logic to OllamaClient",
    "OllamaClient.complete() should retry up to 3 times on HTTP 5xx " +
    "with exponential backoff (100ms, 200ms, 400ms). Log each retry attempt.",
    "demos/phase1-local-serving/src/main/java/LocalVsHostedDemo.java"
)
```

Expected outcome: modified `LocalVsHostedDemo.java` with retry wrapper,
`mvn test` passes, PR opened (or simulated PR output).

---

## Open Questions to Explore

1. **Gate UX**: blocking on stdin is crude. What's a better human-in-the-loop interface?
   (Slack message? Email? Web UI?) — out of scope for Phase 8 but worth knowing the options.

2. **Parallel coding**: what if two tickets come in simultaneously?
   Each needs its own git branch + checkpoint store. How do you isolate?

3. **Eval vs Observability**: you're logging token counts. But how do you know if the CODE is good?
   That's evals (Phase 9 territory). What's the simplest eval you could add here?

4. **Cost modeling**: at what token spend should the pipeline abort and escalate?
   What's the right threshold for a "simple" ticket vs "complex" one?

5. **The human gates paradox**: more gates = safer but slower. At what agent maturity
   do you remove Gate 1? How do you know the agent is ready?

---

## Success Criteria

- [ ] Pipeline runs DEMO-001 start to finish
- [ ] Gate 1 pauses and waits for human input (y/n)
- [ ] Gate 2 pauses and waits for human input (y/n)
- [ ] Checkpoint JSONL written with all steps
- [ ] Summary printed: total tokens, cost, duration, outcome
- [ ] Tests actually run via `mvn test`
- [ ] Rejection at Gate 1 → pipeline re-plans with feedback
- [ ] 3 consecutive test failures → escalate (print + stop, don't loop forever)
- [ ] You can draw the state machine diagram from memory
- [ ] Slide: "Autonomous system with control points"

---

## Dependencies

- Phase 4: agent loop
- Phase 5: java-standards skill
- Phase 6: MCP (file reading via MCP server)
- Phase 7: CoderAgent + ReviewerAgent
- `gh` CLI for PR opening (already installed if you use Claude Code)

## Estimated Effort

8–12 hours (most complex phase — orchestrating all previous phases + new infrastructure)

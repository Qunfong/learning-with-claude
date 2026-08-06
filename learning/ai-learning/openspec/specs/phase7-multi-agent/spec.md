# Phase 7 — Multi-Agent & A2A

## What This Phase Is About

One agent hits ceilings: context window, specialization, parallelism.
Multi-agent systems route work to specialized agents and coordinate their results.
A2A (Agent-to-Agent) is Google's open protocol for this coordination.
Phase 7 builds two agents that collaborate to ship a small feature.

---

## Core Concepts

### Why Multi-Agent?

```
SINGLE AGENT                        MULTI-AGENT
──────────────────────────────────────────────────────────
One context window                  Each agent has own context
= everything must fit               = can run in parallel

One model for all tasks             Right model per task
= generalist compromises            = coder model + reviewer model

Sequential                          Parallel (independent tasks)
= slow for complex work             = 10x throughput on parallelizable work

Hard to debug (one blob)            Traceable (message between agents)
```

### Multi-Agent Topologies

```
ORCHESTRATOR (hierarchical)         PEER-TO-PEER (flat mesh)
──────────────────────────          ──────────────────────────────
       User                               User
        │                                  │
   ┌────▼────┐                        ┌───▼───┐
   │Orchestr.│                        │   A   │
   └────┬────┘                        └──┬─┬──┘
        │                                │ │
   ┌────┴────┐                      ┌───▼─▼───┐
   │    │    │                      │    B    │
   ▼    ▼    ▼                      └─────────┘
  A1   A2   A3

Best for: known workflow             Best for: emergent coordination
          clear delegation                     no central bottleneck

→ USE THIS for Phase 7              → Harder to debug, explore later
```

### A2A Protocol Concepts

```
TASK CARD — the unit of work in A2A

{
  "id": "task-123",
  "type": "code.review",
  "input": {
    "code": "...",
    "context": "implement retry logic"
  },
  "state": "submitted",       ← submitted | working | done | failed
  "artifacts": [              ← output files/data
    {"type": "java_file", "content": "..."}
  ]
}

AGENT CARD — how agents advertise themselves

{
  "name": "ReviewerAgent",
  "capabilities": ["code.review", "test.review"],
  "endpoint": "http://localhost:8081/a2a"
}
```

### Message Flow for the Build

```
User: "Add retry logic to HttpClient calls"
          │
          ▼
┌─────────────────────┐
│   OrchestratorAgent  │   ← plans, delegates, collects
└────────┬────────────┘
         │
   ┌─────┴──────┐
   ▼            ▼
┌──────┐    ┌────────┐
│Coder │    │Reviewer│   ← run sequentially (coder first)
│Agent │───▶│ Agent  │
└──────┘    └────────┘
   │              │
   └──────┬───────┘
          ▼
   Final Java class with retry,
   reviewed and annotated
```

---

## Build Specification

### Two Agents to Build

**CoderAgent**
- Receives: feature description + existing code context
- Calls: Phase 4 agent loop (generate code)
- Outputs: Java class as A2A artifact
- Capability: `code.generate`

**ReviewerAgent**
- Receives: Java code artifact from CoderAgent
- Calls: Phase 5 skill (java-standards) + LLM review
- Outputs: annotated code + list of issues
- Capability: `code.review`

**OrchestratorAgent** (thin)
- Receives user request
- Delegates to CoderAgent, waits for artifact
- Passes artifact to ReviewerAgent
- Collects and presents final result

### A2A Implementation Choice

**Option A: Real A2A spec** (Google's open protocol, JSON-RPC over HTTP)
- More realistic, more complex
- Requires HTTP server per agent

**Option B: Simplified A2A** (same concepts, Java interfaces + in-process)
```java
interface A2AAgent {
    AgentCard card();
    TaskResult handle(Task task) throws Exception;
}
```
- Faster to implement, same learning value
- Recommended for Phase 7 — add HTTP transport in Phase 8 if needed

### Demo Scenario

Input ticket: `"Add exponential backoff retry to OllamaClient.complete()"`

Expected flow:
1. Orchestrator reads `OllamaClient.java` via MCP (Phase 6)
2. Delegates to CoderAgent → generates retry wrapper
3. CoderAgent artifact → ReviewerAgent
4. Reviewer checks against java-standards skill (Phase 5)
5. Reviewer returns: code + issues list
6. Orchestrator presents final code + review summary

---

## Open Questions to Explore

1. What happens when CoderAgent and ReviewerAgent disagree? Who arbitrates?
2. How many agent hops before latency + cost make it not worth it?
3. Agent discovery: how does Orchestrator know ReviewerAgent exists?
   (A2A uses Agent Cards — essentially a service registry)
4. Shared state: if Coder and Reviewer need the same file, who owns it?
5. Failure modes: CoderAgent times out. Does Orchestrator retry, skip, or fail?

---

## Success Criteria

- [ ] CoderAgent generates Java code from a feature description
- [ ] ReviewerAgent reviews against java-standards and returns structured feedback
- [ ] Orchestrator coordinates the full flow end-to-end
- [ ] Retry scenario (ticket above) completes successfully
- [ ] You can draw the message flow diagram from memory
- [ ] You can explain: Orchestrator vs peer-to-peer, with one real tradeoff each
- [ ] Slide: "A2A message flow" with YOUR coder+reviewer diagram

---

## Dependencies

- Phase 4: agent loop (both agents reuse this)
- Phase 5: java-standards skill (Reviewer uses it)
- Phase 6: MCP (Orchestrator reads files via MCP)

## Estimated Effort

6–8 hours (new architecture, three Java classes, coordination logic)

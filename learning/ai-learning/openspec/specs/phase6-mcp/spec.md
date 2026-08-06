# Phase 6 — MCP (Model Context Protocol)

## What This Phase Is About

MCP is Anthropic's open protocol for connecting AI models to external tools and data.
You already use MCP servers right now: `mcp__ide__getDiagnostics`, `mcp__claude_ai_Gmail`, etc.
Phase 6 flips the role: YOU write the server, so agents can talk to YOUR Java service.

---

## Core Concepts

### MCP Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        MCP ECOSYSTEM                            │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│   ┌──────────────┐   MCP Protocol    ┌──────────────────────┐  │
│   │  MCP CLIENT  │ ←────────────────▶│     MCP SERVER       │  │
│   │  (your agent)│                   │  (your Java service) │  │
│   └──────────────┘                   └──────────┬───────────┘  │
│                                                  │               │
│                                        ┌─────────▼──────────┐  │
│                                        │   Your Java API    │  │
│                                        │  (REST / DB / FS)  │  │
│                                        └────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

### The Three MCP Primitives

```
PRIMITIVE       WHAT IT IS                      JAVA ANALOGY
──────────────────────────────────────────────────────────────────
Tools           Executable actions the           @RestController
                model can call (with params)     endpoints (POST)

Resources       Data/files the model can read    @RestController
                (URI-addressable)                endpoints (GET)

Prompts         Reusable prompt templates        Template strings
                with parameters                  the model can invoke
```

The model decides WHEN to call a tool — you define WHAT tools exist.

### MCP Transport Options

```
stdio (simplest)                    HTTP + SSE
──────────────────                  ──────────────────────────
Server reads/writes                 Server is HTTP endpoint
stdin/stdout.                       Client subscribes via SSE.
Zero network config.                Works across machines.
One client only.                    Multiple clients.

→ Use for local dev/learning.       → Use for production.
```

---

## Build Specification

### The Java Service to Wrap

Build a simple **Code Analysis Service** (Spring Boot):
- `GET /files` → list Java files in demos directory
- `GET /files/{name}` → read content of a file
- `POST /analyze` → run basic analysis (line count, method count, TODO count)
- `GET /metrics` → aggregate stats across all demo files

This is real: the service knows your own codebase.

### MCP Server (Java)

```
MCP Tools to expose:
  list_demo_files()         → calls GET /files
  read_demo_file(name)      → calls GET /files/{name}
  analyze_file(name)        → calls POST /analyze
  get_codebase_metrics()    → calls GET /metrics

MCP Resources to expose:
  demo://phase0/**          → phase0 source files
  demo://phase1/**          → phase1 source files
  demo://phase2/**          → phase2 source files
```

### Integration with Phase 4 Agent

```
WITHOUT MCP                         WITH MCP
──────────────────────              ──────────────────────────────
Agent: "I don't know                Agent calls list_demo_files()
what files exist in                 → sees LocalVsHostedDemo.java
your demos"                         → calls read_demo_file(...)
                                    → analyzes it
                                    → gives grounded answer
```

### Implementation Notes

**Option A: MCP Java SDK** (official, recommended)
```xml
<dependency>
    <groupId>io.modelcontextprotocol.sdk</groupId>
    <artifactId>mcp</artifactId>
    <version>0.9.0</version>
</dependency>
```

**Option B: Implement protocol manually**
MCP over stdio is just JSON-RPC 2.0 on stdin/stdout. Educational to implement once.
Not recommended for production.

Transport: start with **stdio** (run server as subprocess from agent).

---

## Open Questions to Explore

1. How does the agent decide WHICH tool to call? (Tool descriptions matter — this is prompt engineering at schema level)
2. What's the difference between a MCP Tool and a function call tool (Phase 3)?
   (MCP = protocol layer; function calling = model capability — they compose)
3. How do you version MCP server tools? What happens to agents mid-conversation when you change a tool schema?
4. Security: your MCP server has file system access. What's the blast radius if the agent goes wrong?

---

## Success Criteria

- [ ] Spring Boot service running on port 8080 with 4 endpoints
- [ ] MCP server exposes ≥ 3 tools and ≥ 1 resource
- [ ] Phase 4 agent can list and read demo files via MCP
- [ ] Agent answers "how many methods does LocalVsHostedDemo.java have?" using MCP tools
- [ ] You can explain: Tools vs Resources vs Prompts in one sentence each
- [ ] Slide: "MCP server/client architecture" with YOUR architecture diagram

---

## Dependencies

- Phase 4: agent loop (MCP client side)
- Spring Boot (new dependency — `spring-boot-starter-web`)

## Estimated Effort

4–6 hours (new Spring Boot project + MCP server wiring)

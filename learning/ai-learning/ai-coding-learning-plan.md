# AI-for-Coding Learning Plan

**Profile:** Java dev (7y), knows RAG basics + transformer internals, comfortable using Claude/Gemini as coding tools.
**Goal:** Ground-up understanding → build agents → A2A → autonomous systems.
**Format:** Each module ends with a hands-on build + a slide (deck grows as you go — one deck, new section per module).

---

## Phase 0 — Foundations Refresh (skip what you already know)
- Tokens & tokenization (BPE), context windows, embeddings vs tokens
- Model parameters: what "7B/70B" means, weights, quantization (GGUF, AWQ, GPTQ)
- Inference basics: temperature, top-p/top-k, logits, sampling
- **Build:** run a local model (Ollama or llama.cpp) with a Java client; log token usage per call
- **Slide:** "How an LLM turns text into text"

## Phase 1 — Local Models & Serving
- Local vs hosted models: tradeoffs (latency, cost, privacy, capability)
- Model families landscape (open weights vs closed) — quick current-state comparison
- Serving: Ollama, vLLM, llama.cpp; quantization tradeoffs in practice
- **Build:** small Java/Spring service that swaps between local model and Claude API via one interface
- **Slide:** "Local vs hosted decision matrix"

## Phase 2 — Prompting & Context Engineering
- System/user/assistant roles, prompt structure, few-shot
- Context window management, chunking, summarization strategies
- RAG deep-dive (you know basics) → hybrid search, reranking, chunk strategy for code
- **Build:** RAG over your own Java codebase (retrieve relevant classes for a question)
- **Slide:** "Context engineering patterns"

## Phase 3 — Tools & Function Calling
- Tool/function calling mechanics (schemas, tool_use loops)
- Structured outputs, JSON mode, validation
- **Build:** single Java tool-calling agent (e.g., "query my DB, summarize result")
- **Slide:** "Anatomy of a tool call"

## Phase 4 — Agents
- Agent loop: plan → act → observe → repeat
- Memory (short-term/session vs long-term/persistent)
- Error handling, retries, guardrails, cost/latency tradeoffs
- **Build:** coding agent that can read/edit files and run tests (mini Claude-Code-style loop) in Java
- **Slide:** "Agent loop architecture"

## Phase 5 — Skills & Domain Knowledge
- What "skills" are (packaged instructions + resources, like Anthropic's skill format)
- Building a custom skill for your agent (Java project conventions, house style)
- Local domain knowledge injection: RAG vs fine-tuning vs skills — when to use which
- **Build:** a skill for your agent encoding your team's Java coding standards
- **Slide:** "Skills vs RAG vs fine-tuning"

## Phase 6 — MCP (Model Context Protocol)
- MCP architecture: servers, clients, tools/resources/prompts
- Writing an MCP server (expose a Java service's data/actions to any MCP client)
- Connecting multiple MCP servers to one agent
- **Build:** MCP server wrapping a Java REST API; connect it to your Phase 4 agent
- **Slide:** "MCP server/client architecture"

## Phase 7 — Multi-Agent & A2A
- Why multi-agent: specialization, parallelism, separation of concerns
- A2A (agent-to-agent) protocol concepts: discovery, messaging, task delegation
- Orchestrator vs peer-to-peer topologies
- **Build:** two agents (e.g., "coder" + "reviewer") communicating via A2A to ship a small feature
- **Slide:** "A2A message flow"

## Phase 8 — Autonomous Systems
- Long-running agents: state persistence, checkpointing, human-in-the-loop gates
- Observability: tracing, evals, cost/latency dashboards
- Safety rails: sandboxing, permission scopes, rollback
- **Build:** autonomous pipeline — ticket in → agent plans → codes → tests → opens PR, pausing for approval at defined gates
- **Slide:** "Autonomous system with control points"

## Phase 9 — Capstone
- Combine everything: local+hosted model routing, RAG over your codebase, custom skill, MCP-exposed tools, multi-agent A2A, autonomy with human gates
- Final deck: full architecture diagram + demo

---

### Suggested cadence
Balanced pace → ~1 module every 1–2 weeks, build-first then backfill theory as needed.

### Next step
Say "start Phase 0" (or any phase) when ready, and we'll go hands-on with your Java stack.

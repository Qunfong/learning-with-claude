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

---

Phases 9-14 close six concrete gaps found by benchmarking `phase0-8` against
AWS's 14-module "Build AI agents with AWS" course — see
`learning/ai-learning-gap-review/NOTES.md` for the full comparison. Each
phase closes exactly one gap.

## Phase 9 — Agent Identity & IAM (Gap 1)
- Principal chains (EndUser → Orchestrator → Specialized Agent), scoped + time-bounded credentials, delegation vs. impersonation
- Credentials never enter the model's context window; out-of-band attachment; append-only audit trail
- **Build:** credential broker + guarded tool invoker modeling AWS STS AssumeRole chains, four demo scenarios (expiry, scope escalation, delegation vs impersonation, prompt-injection credential theft)
- **Slide:** "Principal chain & blast radius: delegation vs impersonation"

## Phase 10 — Framework & Protocol Standards (Gap 3)
- Real A2A agent-card discovery over HTTP (trusted-origin allowlisting, malformed/unreachable/untrusted-origin handling)
- x402 payment-required/retry flow (Coinbase's HTTP agent-payment protocol shape)
- **Build:** embedded HTTP agent-card servers + a runtime capability registry built by live discovery; a mock 402-pause-retry pipeline
- **Slide:** "Agent card discovery vs compile-time wiring"

## Phase 11 — Resilient Multi-Agent Pipeline (Gap 4)
- Circuit breakers (failure-streak, not single-call retry), schema-validated handoffs, confidence scoring & abstention
- **Build:** Planner → Coder → Reviewer pipeline where every handoff passes a hand-rolled JSON-schema gate, each hop wrapped in its own circuit breaker, low-confidence results trigger abstention instead of silent pass-through
- **Slide:** "Schema gate + confidence gate + circuit breaker, one pipeline"

## Phase 12 — Agent Evaluation Harness (Gap 2)
- AWS's three-layer framework: task-level correctness, trajectory quality, system-level health
- Golden / regression / adversarial dataset types, LLM-as-judge (scaffolded)
- **Build:** a Java eval library driven by real trace fixtures from Phase 4 and Phase 8, with a CI-gate-worthy composite score
- **Slide:** "Three eval layers, one composite score"

## Phase 13 — Memory: ANN Index, Hybrid Search & Graph Memory (Gap 5)
- Hand-rolled HNSW-lite ANN index vs. brute-force scan, BM25+vector hybrid search with RRF, cross-encoder-style re-rank, Graph RAG
- **Build:** benchmark harness comparing brute-force vs ANN comparison counts at increasing scale; a ranked/bounded/decayed memory store next to the old unranked one
- **Slide:** "Brute force vs ANN: comparisons don't grow linearly"

## Phase 14 — Domain Specialization (Gap 7)
- AWS's four levers: system prompt, knowledge corpus, tool selection, guardrails — applied to one vertical (customer support)
- **Build:** domain agent vs. baseline agent run side-by-side on four scenarios, same model/tools, only the four levers differ
- **Slide:** "Four levers, one baseline comparison"

## Phase 15 — Capstone
- Combine the throughline across all 15 phases: scoped credentials → agent loop → tool calling → schema-validated multi-agent handoff with confidence scoring → memory retrieval → eval/judge scoring
- **Build:** a combined demo chaining a representative slice of the curriculum in one run, plus a generated slide deck (`SlideDeckGenerator` → `SLIDES.md`) synthesizing every phase's key concept and technique diagram
- **Slide:** the generated deck itself is the final artifact — one Mermaid diagram per phase, closing with the combined architecture

---

### Suggested cadence
Balanced pace → ~1 module every 1–2 weeks, build-first then backfill theory as needed.

### Next step
Say "start Phase 0" (or any phase) when ready, and we'll go hands-on with your Java stack.

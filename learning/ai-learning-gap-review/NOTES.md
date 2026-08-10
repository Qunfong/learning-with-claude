# AI Learning Gap Review: AWS "AI Agent Learning Series" vs. `ai-learning/demos/phase0-8`

Source: AWS Marketplace "Build AI agents with AWS" 14-module series
(https://aws.amazon.com/marketplace/build-learn/ai-agent-learning-series). Only
**8 of 14 modules are currently published** with full content (module 9,
"Data pipelines & lineage," has a webinar registration link only, no content
page; modules 10-14 aren't live at all yet). This review covers the 8 published
modules against the user's `phase0-tokens-inference` through `phase8-autonomous`
demos plus `ai-coding-learning-plan.md`.

---

## Part 1 — AWS module-by-module summary

### Module 1: Introduction to AI agents
Foundations: brief history (rule-based → ML → LLMs → agents), LLMs as the
reasoning layer, context windows and "context engineering," short-term vs
long-term memory, the thought-action-observation loop, the three-layer
architecture (reasoning / orchestration / tools), design patterns (single-agent
loop, supervisor-worker, swarm, human-in-the-loop), interoperability (MCP,
A2A — teased for Module 4), and a getting-started guide (design → develop →
evaluate) with a Strands Agents SDK code sample.

### Module 2: AI agent frameworks & building blocks
Framework taxonomy: workflow frameworks (LangGraph, Bedrock Agents),
LLM-integration frameworks (LangChain, LlamaIndex), data-centric frameworks
(LlamaIndex, Haystack), multi-agent frameworks (CrewAI, AutoGen, Strands).
The ReAct pattern (Yao et al., ICLR 2023) with benchmark data. LangChain
Expression Language (LCEL) primitives (RunnableSequence/Parallel/Lambda/
Passthrough/Branch). Deep dive on reasoning/orchestration/tool layers.
**Three interoperability protocols**: MCP (tools), **A2A (agent-to-agent,
Google, April 2025)**, and **x402 (Coinbase's HTTP-based agent payment
protocol, May 2025)**. Model landscape (frontier/local/VLM/speech/embedding),
multi-model routing. Production-readiness patterns mapped to AWS
Well-Architected pillars (cost, performance, sustainability). Four
multi-agent architecture patterns: subagents, skills, handoffs, router.

### Module 3: Agent evaluation & decision engines
Why agent evaluation differs from classical software testing (probabilistic,
path-dependent, subjective). A formal **three-layer evaluation framework**:
task-level correctness (exact/fuzzy match, rubric scoring, reference-free
LLM-as-judge), trajectory quality (tool call accuracy, argument quality, step
efficiency, error handling), system-level health (latency, cost, task success
rate). Evaluation dataset types: golden, regression, adversarial,
production-derived — plus the "cold start problem." Automated evaluation
techniques: LLM-as-judge (with bias mitigation), rubric-based scoring,
embedding-based similarity, structured output validation. Tracing/observability
as evaluation infrastructure (OpenTelemetry → Amazon Bedrock AgentCore
Evaluations). Decision engines (think-act-observe, chain-of-thought, tool
selection/planning). Confidence, uncertainty, and guardrails (input/output
filtering, escalation design). Evaluation across the dev lifecycle
(prototyping → pre-production → production → evaluation-driven development).
Full worked example evaluating a "DevOps Companion" agent end-to-end.

### Module 4: Multi-agent architectures
Why move to multi-agent (context saturation, specialization, parallelism,
fault isolation). **Four planes** of a multi-agent system: control
(orchestration), execution (specialized agents), state (shared memory), and
capability (tools/MCP). **Four orchestration patterns**: centralized,
skill-based dispatch, handoff chains, parallel fan-out+synthesis. Compounding
non-determinism math (four 90%-accurate agents chained ≈ 66% overall success)
and why **structured, schema-validated handoffs** are the fix. Confidence
scoring and abstention. Shared context design (task state vs. session context
vs. domain knowledge, each mapped to a different AWS store). MCP multi-tenancy
and versioning; **A2A protocol in depth** (task objects, agent cards,
security). Trust/identity in multi-agent systems (least-privilege scoped
delegation, prompt-injection propagation across agent boundaries, zero-trust
boundaries). Failure modes: cascading failures (circuit breakers), orchestration
loops/runaway agents, conflicting parallel outputs, context corruption.
Distributed tracing across agent boundaries.

### Module 5: Domain-specific agent applications
The **four levers of domain specialization**: system prompt, knowledge
corpus, tool selection, guardrails. Applied to three verticals: customer
engagement/sales (intent classification, personalization, escalation design),
location-aware/logistics agents (why models shouldn't do spatial computation
themselves — use geospatial tools; freshness/caching tradeoffs for
traffic vs. static geo data), and voice-enabled agents (STT/TTS pipeline,
strict latency budgets, response-length constraints, interruption handling,
multilingual design). Domain-specific evaluation (domain-representative
golden datasets, domain-expert involvement). Integrating domain agents as
composable subagents in a multi-agent architecture.

### Module 6: Agent orchestration
The gap between prototype and production (transient errors, infra failures,
partial completion, long-running duration, human gates, compliance/audit).
**Durable execution** (Step Functions, Temporal) — checkpointing so a
20-minute, non-idempotent workflow doesn't restart from scratch on failure;
idempotency keys and compensation patterns. **Agent/tool/MCP lifecycle
management**: semantic versioning, agent aliases, canary rollout with online
evaluation gating promotion. Dependency management (explicit declarations,
circuit breakers for failing dependencies). Scheduling/event-driven
orchestration (EventBridge Scheduler, SQS, Prefect, Airflow/Astronomer —
including deferrable operators and dataset-based scheduling to avoid
double-retry storms). Concurrency, rate limiting, backpressure, dead-letter
queues. **Human-in-the-loop via the task-token pattern** (pause a workflow
indefinitely with zero compute cost, resume via callback). Observability at
the workflow/agent/dependency level with correlated trace IDs, operational
runbooks.

### Module 7: Agent memory systems
A memory taxonomy along two axes (duration × scope): in-context working
memory, short-term session memory (LangGraph checkpointer, Redis vs.
DynamoDB backend tradeoffs), long-term semantic memory (embeddings, vector
index architectures — flat, **HNSW**, IVF — hybrid search with BM25+RRF,
cross-encoder re-ranking, 7 vector databases compared), and shared cross-agent
memory (the Module-4 state plane revisited). Redis Cloud vs. MongoDB Atlas
roles (hot path vs. cold/unified path). **Graph RAG** with Neo4j —
relationship-aware retrieval for structurally-connected domains
(dependencies, causality, ownership) that vector similarity handles poorly.
Memory governance and consolidation as a data discipline (extracting durable
facts from session history into long-term stores).

### Module 8: AI agent identity and access management
Why agents are a "third generation" IAM problem service accounts don't
handle: delegation chains, dynamic per-phase scope, credential expiry on
long-running workflows. Precise principal taxonomy (End User → Orchestrator
→ Specialized Agent → MCP Server) and **impersonation vs. delegation**. AWS
IAM foundation: one role per component, permission boundaries as a
privilege-escalation defense, **STS AssumeRole chains** for time-bounded
task-scoped credentials. Credential management: no hardcoded credentials,
Secrets Manager with automatic rotation, credential delivery through the MCP
server layer (never into the agent's context window). **OAuth 2.0** for
user-delegated and machine-to-machine authorization, scopes as the
authorization currency, the MCP authorization model layering OAuth on top of
IAM. Multi-tenant federated identity (IAM Identity Center, tenant isolation
via DynamoDB partition-key IAM conditions). Securing agent-to-agent
communication (IAM SigV4, mTLS, agent card discovery from trusted sources
only, defending against orchestrator impersonation). **Prompt injection as a
credential-theft vector** with a defense-in-depth model (no credentials in
context → tool scope restriction → Bedrock Guardrails → runtime monitoring).
Audit/compliance/non-repudiation (CloudTrail, S3 Object Lock COMPLIANCE mode,
Athena queries, full delegation-chain reconstruction via session tags).

---

## Part 2 — Concrete gaps

Numbered by how directly they matter for a working Java/AI engineer, not by
module order.

**1. Agent identity, credentials, and IAM — total gap.**
AWS Module 8 is an entire module on this; none of phase0-8 touches it at all.
`phase4-agents/CodingAgentDemo`'s `confirmHook` is a crude "ask before
destructive action" gate, and `phase8-autonomous`'s Gate 1/Gate 2 are human
approval checkpoints — but neither is authorization. There is no concept in
any demo of: scoped/time-bounded credentials, one-IAM-role-per-agent-component,
OAuth delegation vs. impersonation, credentials never entering the model's
context window (AWS's Module 8 identifies this as the single most important
prompt-injection defense), or an audit trail that can survive a dispute. This
matters because it's the first thing an enterprise security team will ask
about before any of these demos' techniques go anywhere near production —
and the user is specifically a Java backend dev who will be expected to know
this stack (IAM, STS, Secrets Manager rotation) cold.

**2. Formal agent evaluation — shallow vs. AWS's three-layer framework.**
AWS Module 3 defines task-level correctness, trajectory quality, and
system-level health as three separate measurement layers, with golden/
regression/adversarial/production-derived datasets and LLM-as-judge bias
mitigation (positional/verbosity/self-reinforcement bias, mitigated by using
different model families for generation vs. judging). The user's closest
analog is `phase4-agents/trace.jsonl` (good instinct — "never trust the
model's own summary, verify against the trace" is literally AWS's trajectory-
quality argument) and `phase7-multi-agent/ReviewerAgent`'s static-regex +
LLM-as-reviewer pass. But there's no golden dataset anywhere in the repo, no
regression suite, no adversarial test set, and no discussion of LLM-judge
bias. `phase7`'s LLM review pass is directionally an LLM-as-judge, but it
judges code quality once per run rather than being a repeatable, versioned
evaluation harness with a scored baseline to regress against.

**3. No framework or protocol-standard exposure (LangChain/LangGraph/
Bedrock Agents, A2A, x402).**
Every demo hand-rolls the agent loop, tool schema, and MCP transport from
raw `HttpClient`/Jackson — a deliberate and valuable choice for understanding
mechanics (the demos are explicit about this), but it means the user has
never seen what a production framework actually buys you: LangGraph's
durable-graph execution and checkpointer abstraction, LCEL composition,
Bedrock Agents' managed ReAct loop, or CrewAI/AutoGen's multi-agent
primitives. `phase7-multi-agent`'s "Agent discovery" open question explicitly
notes the orchestrator wires up `A2AAgent` references at compile time and
that "real A2A's answer is Agent Cards served from a well-known URL" — the
user has correctly identified the gap but not built it. **x402 (agent
payments) isn't mentioned anywhere** in the learning plan or demos at all.

**4. Production multi-agent failure handling — partially covered, shallow.**
`phase7`/`phase8` demonstrate real, valuable failure modes (organic ESCALATE
runs from a missing dependency and a renamed method contract, `--chaos-fail`
for deterministic testing) — this is good, evidence-based engineering. But
AWS Module 4's specific vocabulary and mechanisms are absent: no **circuit
breakers** wrapping a failing dependency (vs. phase8's simple retry-then-
escalate), no schema-validated handoff contracts enforced at the boundary
(phase7's Task/TaskResult are plain Java records with no runtime schema
validation), no confidence scoring/abstention (an agent choosing not to
answer rather than guessing), and no compounding-non-determinism math to
reason about how many agent hops a pipeline can tolerate before reliability
degrades unacceptably.

**5. Memory: no vector database, no hybrid search/re-ranking, no graph
memory.**
`phase2-prompting-rag`'s `VectorStore` is explicitly an in-memory `List` with
brute-force cosine similarity — fine for the chunking-strategy lesson it's
teaching, but the user has never touched an actual ANN index (HNSW/IVF),
hybrid BM25+vector search, or cross-encoder re-ranking, all of which AWS
Module 7 covers in production depth (including a 7-vector-database
comparison: Pinecone/Weaviate/Qdrant/Zilliz/MongoDB Atlas/Redis/Neo4j).
`phase4-agents/MemoryDemo`'s `recall()` is explicitly called out in the
demo's own README as unranked and unbounded ("a real risk... in production:
expiry, relevance-ranking, or periodic review") — the user already flagged
the gap but hasn't built the fix. **Graph RAG (Neo4j) isn't touched at all**
— relevant for exactly the kind of DevOps/dependency-graph domain the user's
own demos use as their running example.

**6. Durable execution and workflow-engine concepts — conceptually parallel
but not built on real infrastructure.**
`phase8-autonomous`'s state machine (PLANNING → GATE1 → CODING → TESTING →
GATE2 → PR, with RETRY×3→ESCALATE) is a genuinely good hand-built parallel to
AWS Module 6's durable-execution model, and the checkpoint JSONL files are a
reasonable homegrown analog to Step Functions' execution history. But it's a
single JVM process with no real crash recovery: if the process dies mid-run,
nothing resumes it from the last checkpoint (the checkpoint file is a
write-only audit log, not a resumable state store). AWS's idempotency-key /
compensation-pattern discussion, agent/MCP-server semantic versioning with
canary aliases, and scheduling infrastructure (EventBridge Scheduler,
Airflow deferrable operators) have no counterpart anywhere in the demos.

**7. Domain specialization (customer-facing, geospatial, voice) — not
explored at all.**
AWS Module 5's entire module (three verticals, "four levers" framework) has
no analog. Every demo is a generic dev-tooling / DevOps-flavored agent
(coding, code review, MCP file/receipt servers). This is a reasonable scope
choice for a Java backend learning plan, but worth naming explicitly: the
user has never practiced adapting a system prompt + knowledge corpus + tool
scope + guardrails for a specific business domain, nor hit domain-specific
constraints like voice's latency budget (STT/TTS pipeline, sub-2-second
response requirement) or the "never let the model do spatial math itself"
principle.

---

## Part 3 — What the user's demos cover that AWS's course doesn't (brief)

- **Sampling/tokenization math built from first principles**
  (`phase0/SamplingDemo`: softmax, temperature-before-softmax, top-k vs.
  top-p behavior on a visible toy distribution) — AWS Module 1 mentions
  temperature/sampling only at a conceptual level, never derives it.
- **Local model serving specifics** (Ollama, quantization formats) — AWS's
  content is Bedrock-hosted-model-centric; local/self-hosted serving
  tradeoffs (the user's own Phase 1) aren't covered by AWS at all.
- **Hand-rolled MCP protocol over stdio JSON-RPC, no SDK** (`phase6-mcp`) —
  AWS discusses MCP at the architecture/adoption-statistics level; the user
  has literally implemented the wire protocol and independently found and
  fixed a real bug (subprocess `cwd` inheritance breaking relative file
  resolution) that no amount of reading AWS's module would have surfaced.
- **Empirical, repeated-run documentation of small local-model failure
  modes** — fabricating a successful tool call in natural-language output
  without ever invoking the tool, mangling escaped content in large
  `write_file` arguments, ignoring an available aggregation tool in favor of
  eyeballing raw JSON. This is exactly the kind of hard-won, run-it-and-see
  evidence AWS's prose-and-diagram format doesn't produce.
- **Skills as literal system-prompt injection with a token-cost projection**
  (`phase5-skills`) — AWS Module 2 references Anthropic's skill format only
  in passing; the user's demo quantifies the actual per-call token/cost
  tradeoff of shipping a skill at scale.
- **Guardrails demonstrated with live, reproducible trace evidence**
  (`phase4-agents/GuardrailsDemo`) rather than described in prose — each
  guardrail (maxIterations, tokenBudget, loopDetection, confirmHook) is shown
  actually tripping, with the exact `trace.jsonl` line that proves it.

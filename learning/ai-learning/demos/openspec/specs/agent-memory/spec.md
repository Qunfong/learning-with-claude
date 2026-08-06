## Purpose
Short-term (in-run) vs long-term (persisted, tool-mediated) agent memory, built in phase4-agents.

## Requirements

### Requirement: Long-term memory persists facts across separate process runs
`MemoryDemo` SHALL expose a `remember` tool that writes a fact to a persisted JSON store, and a `recall` tool that reads from it, such that facts survive independent process executions.

#### Scenario: Fact stored in one run is recalled in a later separate run
- **WHEN** `MemoryDemo` run A calls `remember(fact)` and the process terminates
- **THEN** a subsequent, separately started `MemoryDemo` run B calling `recall(query)` retrieves the fact from the persisted store, proving it survived the process restart

### Requirement: Long-term memory access is a visible structured tool call
Long-term memory reads/writes SHALL happen only via explicit `remember`/`recall` tool calls (structured JSON name + arguments), validated the same way any other tool call is validated, never via hidden framework behavior.

#### Scenario: Memory access is inspectable like any tool call
- **WHEN** the agent stores or retrieves a long-term fact
- **THEN** the action appears in console output as a named tool call with explicit arguments, identical in shape to phase3's tool-calling pattern

### Requirement: Short-term and long-term memory are demonstrated as distinct
`MemoryDemo` SHALL demonstrate both memory kinds in the same run so the distinction (dies with the run vs deliberately persisted) is directly observable.

#### Scenario: Same run shows both memory kinds side by side
- **WHEN** `MemoryDemo` runs
- **THEN** it shows a fact held only in the in-run message list (lost if the process restarts) alongside a fact written via `remember` (retrievable after restart via `recall`)

### Requirement: Long-term memory risk is documented
The `MemoryDemo` README SHALL document the risk of unbounded memory growth and of stale or wrong memories poisoning later runs, and SHALL state what a production system would add to mitigate it.

#### Scenario: README names the risk and mitigation direction
- **WHEN** a reader reviews `MemoryDemo`'s README risk/simplifications section
- **THEN** it explicitly states the unbounded-growth and stale-memory-poisoning risks and names production mitigations (e.g. expiry, relevance ranking, review) without implementing them

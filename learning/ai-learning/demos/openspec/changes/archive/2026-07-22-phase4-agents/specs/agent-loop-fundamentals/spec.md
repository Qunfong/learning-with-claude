## ADDED Requirements

### Requirement: Naive agent loop runs unbounded
`AgentLoopDemo` SHALL execute a plan→act→observe→repeat loop against a tool-capable model with no iteration cap, no budget, and no loop-detection, so that an unguarded failure mode is directly observable.

#### Scenario: Loop produces an unguarded failure mode
- **WHEN** `AgentLoopDemo` runs a task with an unsatisfiable stop condition (no tool exists to make it true)
- **THEN** the loop has no configured reason to stop other than the model's own choice, and the console output makes the resulting failure (repeated/batched identical calls, and/or a false completion claim) visible as the demo's point

### Requirement: Short-term memory is the in-run message history
The agent loop SHALL treat the growing per-run message list as its only memory, and this SHALL be named explicitly (not left implicit) in `AgentLoopDemo`'s output or README.

#### Scenario: Agent references an earlier turn within the same run
- **WHEN** the agent needs information stated earlier in the same `AgentLoopDemo` execution
- **THEN** it retrieves it from the accumulated messages list passed into each model call, with no external store involved

### Requirement: Guardrailed loop enforces a max-iteration cap
`GuardrailsDemo` SHALL halt the loop once a configured maximum iteration count is reached, even if the model has not naturally produced a final answer.

#### Scenario: Loop halts at the iteration cap
- **WHEN** the loop reaches the configured max-iteration count before the model stops issuing tool calls
- **THEN** the loop halts and reports a "max iterations reached" guardrail message instead of continuing

### Requirement: Guardrailed loop enforces a token/cost budget
`GuardrailsDemo` SHALL track cumulative token usage across turns and halt before the next model call if a configured budget would be exceeded.

#### Scenario: Loop halts on budget breach
- **WHEN** cumulative token usage across prior turns exceeds the configured budget
- **THEN** the loop halts before issuing the next model call and reports the budget breach

### Requirement: Guardrailed loop detects repeated identical tool calls
`GuardrailsDemo` SHALL compare each proposed tool call (name + arguments) against the immediately preceding one and abort the loop if they are identical.

#### Scenario: Duplicate call aborts the loop
- **WHEN** the model issues a tool call with the same name and arguments as the previous turn's call
- **THEN** the loop aborts with a "loop detected" message instead of executing the duplicate call

### Requirement: Destructive tool calls require a confirmation hook
`GuardrailsDemo` SHALL route any tool call marked destructive through a confirm-hook before execution, and SHALL NOT execute it without confirmation.

#### Scenario: Confirm-hook blocks an unconfirmed destructive call
- **WHEN** the model requests a tool call marked destructive
- **THEN** the loop invokes the confirm-hook and only executes the call if confirmed, otherwise aborts that step and reports why

### Requirement: Combined guardrails prevent the naive demo's failure mode
`GuardrailsDemo` SHALL re-run the same unsatisfiable-goal scenario used by `AgentLoopDemo`, with a full guardrail configuration, and SHALL halt cleanly instead of reproducing the naive demo's failure.

#### Scenario: Same scenario, different outcome
- **WHEN** `GuardrailsDemo` runs the identical task `AgentLoopDemo` used
- **THEN** the loop halts via one of the configured guardrails instead of batching repeated calls or producing a false completion claim

### Requirement: Transient tool/network failures are retried with backoff
`OllamaClient` SHALL retry a failed model call a bounded number of times with backoff between attempts when the failure is transient (timeout, 5xx), and SHALL surface the failure if retries are exhausted.

#### Scenario: A simulated transient failure recovers on retry
- **WHEN** a call fails once due to a simulated transient error and succeeds on a subsequent attempt within the retry limit
- **THEN** the loop's overall call succeeds, and the console output shows the retry happened (attempt count, backoff) rather than silently masking it or crashing on the first failure

#### Scenario: Retries are exhausted
- **WHEN** a call keeps failing beyond the configured retry limit
- **THEN** the failure is surfaced to the caller as an error, not retried indefinitely

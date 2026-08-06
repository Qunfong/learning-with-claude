## Purpose
The phase4-agents capstone build: a file-editing, test-running coding agent (read_file/write_file/run_tests) tying the agent loop, guardrails, and retries together into the master plan's actual Phase4 build target.

## Requirements

### Requirement: Capstone agent can read and write real files
`CodingAgentDemo` SHALL expose `read_file` and `write_file` tools that operate on real files within the module's own scratch directory, so the agent can inspect and modify actual source code rather than a text description of it.

#### Scenario: Agent reads a file's current contents
- **WHEN** the agent calls `read_file` with a path inside the scratch directory
- **THEN** the tool returns that file's real, current on-disk contents

#### Scenario: Agent writes a modified file
- **WHEN** the agent calls `write_file` with a path and new contents
- **THEN** the file on disk is overwritten with that content, and a subsequent `read_file` on the same path reflects the change

### Requirement: Capstone agent can run tests and observe the result
`CodingAgentDemo` SHALL expose a `run_tests` tool that executes a real test check against the scratch project and returns pass/fail plus failure details, so the loop's "observe" step reflects real feedback, not a simulated one.

#### Scenario: Failing test reports failure detail
- **WHEN** `run_tests` is called against code that does not satisfy the test
- **THEN** the tool returns a result indicating failure along with enough detail (e.g. expected vs actual) for the agent to act on

#### Scenario: Passing test reports success
- **WHEN** `run_tests` is called after the agent has corrected the code
- **THEN** the tool returns a result indicating success, and this becomes the loop's natural stop condition

### Requirement: Capstone loop fixes a starting failure through repeated read-edit-test cycles
`CodingAgentDemo` SHALL start from a scratch Java file with a known failing test, and the agent loop SHALL iterate read_file → write_file → run_tests until the test passes or a guardrail halts it, demonstrating the full plan→act→observe→repeat cycle on a real (if small) coding task.

#### Scenario: Agent converges on a passing fix
- **WHEN** `CodingAgentDemo` runs against its starting scratch state
- **THEN** the loop performs at least one read→edit→test cycle and ends either with `run_tests` reporting success or a guardrail halting the loop — never with the loop silently giving up without reporting which

### Requirement: Capstone demonstrates retry-with-backoff on a transient failure
`CodingAgentDemo`'s `run_tests` tool SHALL simulate exactly one transient failure (independent of the actual test outcome) so that `OllamaClient`'s retry-with-backoff path is deterministically exercised and visible in the demo's console output.

#### Scenario: Simulated transient failure is retried and recovers
- **WHEN** the simulated transient failure occurs during a `CodingAgentDemo` run
- **THEN** the console output shows a retry attempt with backoff, and the loop continues normally afterward rather than treating the transient failure as a fatal error

### Requirement: Capstone scratch state is isolated and resettable
All files the capstone reads or writes SHALL live under `phase4-agents/workspace/` only, and the README SHALL document how to reset that directory to its starting (failing-test) state for a clean rerun.

#### Scenario: Rerun after a prior successful run
- **WHEN** a reader wants to rerun `CodingAgentDemo` after it has already fixed the code once
- **THEN** the README's reset instructions restore the scratch directory to its original failing state, and no phase0-3 module or other part of the repo is affected by any capstone run

### Requirement: Ground truth is independently verifiable via structured tracing
Because the model's final natural-language summary can misrepresent what actually happened (e.g. claiming a fix succeeded when no corresponding tool call was made), `AgentLoop` SHALL log every real tool invocation (iteration, tool name, arguments, status, result) to a structured trace file independent of the model's own final answer, so a reader can verify ground truth without trusting the model's text.

#### Scenario: Model claims an action it never actually took
- **WHEN** the model's final response describes a completed action (e.g. "I fixed and verified it") without a matching `tool_calls` entry having actually been issued for that action
- **THEN** the structured trace log contains no corresponding tool invocation, making the discrepancy verifiable by comparing the trace against the claim — and against the real file/state on disk

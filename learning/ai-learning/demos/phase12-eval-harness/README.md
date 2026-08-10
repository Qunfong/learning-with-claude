# Phase 12 — Demo: Agent Evaluation Harness (Gap 2)

Goal: close Gap 2 from `learning/ai-learning-gap-review/NOTES.md` ("Formal
agent evaluation — shallow vs. AWS's three-layer framework"). Nothing in
`phase0-12` is a repeatable, versioned evaluation harness. The closest analogs
were `phase4-agents/trace.jsonl` (good instinct — "never trust the model's
own summary, verify against the trace" is literally AWS's trajectory-quality
argument) and `phase7-multi-agent/ReviewerAgent`'s static-regex + LLM-as-
reviewer pass, but neither is a scored baseline you can regress against, and
neither has a golden dataset, a regression suite, or an adversarial test set.
This phase builds AWS Module 3's three measurement layers — task-level
correctness, trajectory quality, system-level health — as a small Java
library, driven entirely by real trace fixtures pulled from `phase4-agents`
and `phase8-autonomous`.

**This models the shape of AWS Module 3's evaluation framework — it is NOT
Bedrock AgentCore Evaluations.** There's no managed evaluation service, no
OpenTelemetry span collection (this harness is a batch, offline reader of
already-written JSONL files, not a live tracing pipeline), and no automatic
production-derived dataset pipeline (the "golden" set here is hand-
transcribed from a README's prose, not sampled from live traffic). Most
importantly: `OllamaJudge` is a single local-model LLM-as-judge client, not a
production bias-mitigation pipeline. Its own javadoc describes judging with a
model family different from the one that generated the trajectory (AWS's
self-reinforcement-bias mitigation) and points at a `JudgeVarianceTest` for
proof — **that test does not exist in this module**, and no test anywhere
instantiates `OllamaJudge` or calls `judge(...)`. The LLM-as-judge layer is
wired up as a callable class but is not exercised or verified by anything
that runs; see "Deviations" below.

This is a fully independent Maven module (own `pom.xml`, flat class
structure), same convention as every other phase — no imports from
`phase4-agents/` or `phase8-autonomous/`. It only *reads* trace files copied
in as test fixtures from those phases; it never imports their code.

## No demo `main` — the test suite IS the demo

Unlike every other phase, there is no `PhaseNDemo.java` with a runnable
`main`. This phase is a library/harness, and its 6 JUnit test classes are
real assertions against real trace fixtures — not a script that prints
output for a human to eyeball. "Running the demo" means running the tests
and reading which fixture each one loads and what it asserts.

## Architecture: three layers, one orchestrator, one dataset

| Class | AWS layer | Role |
|---|---|---|
| `TaskLevelEvaluator` | Layer 1 — task-level correctness | Pure exact-match, no LLM: does the trace's own `run_summary.outcome` claim agree with its own last `test_run.passed`? Catches a run that claims success while its last real test failed. |
| `StructuralConformanceValidator` | Layer 2a — trajectory quality (rule-based) | No LLM, no judgment call: replays event order against phase8's state-machine invariants (GATE1 before CODING, a passing test before GATE2, every CODING followed by a test_run, a terminal `run_summary`, ESCALATE and an approved GATE2 are mutually exclusive). |
| `OllamaJudge` | Layer 2b — trajectory quality (LLM-as-judge) | Single-shot Ollama `/api/chat` client that scores a trajectory 1-5 against a written rubric ("did it plan before coding, test before shipping, retry sensibly"). Present and callable, but **not exercised by any test in this module** — see Deviations. |
| `SystemHealthAggregator` | Layer 3 — system-level health | Aggregates tokens/cost/duration across a run set, and independently cross-checks the aggregate against specific figures `phase8-autonomous/README.md` prints in prose (not numbers this harness invented — numbers a human already read off a real terminal). |
| `EvalHarness` | Orchestrator | Ties the layers into two CI-facing operations: `classify(trace)` (SUCCESS/ESCALATE from `run_summary.outcome`) and `score(trace)` (a deterministic 0-100 composite — outcome + test pass ratio + retry efficiency + token efficiency − structural-violation penalty — for a CI gate to threshold on). |
| `GoldenDataset` | Dataset (golden) | 6 hand-labeled `GoldenRun` records, expected outcome and reason transcribed directly from `phase8-autonomous/README.md`'s "What actually happened" section — not derived from the harness itself. |
| `RunTrace` / `TraceLoader` | Infrastructure | Parses phase8's `ObservabilityCollector` JSONL format (`step`/`gate_decision`/`test_run`/`file_written`/`run_summary`, one object per line) into a thin, read-only wrapper. Every evaluator layer pulls the handful of fields it needs directly off the underlying `JsonNode`s. |

## Running it

Run from **this** directory (`demos/phase12-eval-harness/`), offline:

```bash
mvn -o test
```

There's nothing to `exec:java` — read the test classes to see each layer in
action. Each one corresponds to one of AWS's evaluation-dataset types:

| Test class | Dataset type | Fixtures it reads |
|---|---|---|
| `GoldenSetRegressionTest` | Golden | All 6 `phase8-traces/run-DEMO-001-*.jsonl` files, via `GoldenDataset.RUNS` |
| `TaskLevelEvaluatorTest` | Golden (unit-level pin) | Two of the same golden traces — one SUCCESS, one ESCALATE — pinning `TaskLevelEvaluator`'s exact-match rule on the two legitimate real-world shapes |
| `RegressionDetectionTest` | Regression | `synthetic/better-run.jsonl` vs. `synthetic/worse-run.jsonl` — hand-built traces proving `EvalHarness.score` ranks a clean first-try run above a 3-retry escalated one by a CI-gate-worthy margin (≥20 points) |
| `AdversarialTraceTest` | Adversarial | `adversarial/claims-success-but-tests-failed.jsonl` — `run_summary.outcome` says `"pr-simulated"` while the last `test_run` recorded `passed:false`; proves the harness doesn't just trust the summary field |
| `StructuralConformanceValidatorTest` | Adversarial + golden (unit-level pin) | `structural/coding-before-gate1.jsonl`, the same adversarial fixture above, and one clean golden run — pins each structural rule independently of any one fixture |
| `SystemHealthAggregationTest` | Golden (production-derived, historical) | All 6 golden traces, aggregated and cross-checked against the exact token/cost/duration figures `phase8-autonomous/README.md` prints for run 3 and run 6 |

## What each test actually proves

- **`GoldenSetRegressionTest.classifiesAllSixHistoricalRunsCorrectly`** —
  `EvalHarness.classify` reproduces all 6 known-correct labels from real,
  previously-committed Ollama runs (3 organic ESCALATEs from real compiler/
  test failures, 2 clean SUCCESSes, 1 forced ESCALATE via `--chaos-fail`). If
  this ever fails after touching `classify`, that's a regression, not a sign
  the labels are wrong — they're transcribed straight from a README
  documenting live runs. Two companion tests in the same class additionally
  confirm every golden run is also task-level consistent and structurally
  clean, i.e. the real runs never even brush the invariants the adversarial
  fixture is built to violate.
- **`AdversarialTraceTest`** — three tests, two independent layers, one
  hand-crafted lie: `TaskLevelEvaluator` flags the outcome/test mismatch as
  `MISMATCH` (not a silent pass), `StructuralConformanceValidator`
  independently catches the same trace via `TESTING_BEFORE_GATE2` (GATE2 was
  reached with zero passing tests), and `EvalHarness.score` penalizes it well
  below a genuine clean success (`run-DEMO-001-1785276196928.jsonl`) instead
  of the ~70/100 a naive outcome-only scorer would give it.
- **`RegressionDetectionTest`** — `synthetic/worse-run.jsonl` (3 coding
  attempts, escalated, average confidence marker < 0.5) scores at least 20
  points below `synthetic/better-run.jsonl` (first-try, high confidence, low
  token spend) — proving the composite score is a usable CI threshold, not
  noise-sized.
- **`StructuralConformanceValidatorTest`** — pins each rule in isolation
  (`GATE1_BEFORE_CODING` on a fixture built to violate exactly that, plus
  reuse of the adversarial fixture for `TESTING_BEFORE_GATE2`) and confirms a
  real clean golden run (`1785276196928`) produces zero violations.
- **`SystemHealthAggregationTest`** — aggregates all 6 golden traces and
  cross-checks the result against two specific numbers `phase8-autonomous`'s
  own README prints in prose (run 3: 3595 total tokens / $0.0200 / 44.4s;
  run 6: peak token usage was 29% of the 40000 token budget) — the aggregate
  isn't just internally self-consistent, it agrees with a number a human
  already read off a real terminal in a different phase.
- **`TaskLevelEvaluatorTest`** — sanity-checks the exact-match rule directly
  on the two legitimate real-world shapes (SUCCESS+passing test,
  ESCALATE+failing test), independent of the adversarial mismatch case.

**Real output** (`mvn -o test`, this directory): 16/16 tests pass, 0
failures, 0 errors, across the 6 classes above (`AdversarialTraceTest` 3,
`GoldenSetRegressionTest` 3, `RegressionDetectionTest` 2,
`StructuralConformanceValidatorTest` 3, `SystemHealthAggregationTest` 3,
`TaskLevelEvaluatorTest` 2).

## Deviations / what's missing vs. AWS Module 3

- **`OllamaJudge` is unverified.** It compiles, has a rubric, and would call
  a local Ollama model — but no test in this module ever calls it. The class
  javadoc references a `JudgeVarianceTest` that would run the same
  trajectory through two different judge models and record where they
  disagree (the actual point of AWS's bias-mitigation guidance); that test
  was never written. Treat `OllamaJudge` as scaffolding for the LLM-as-judge
  layer, not as a demonstrated one — there is no evidence in this repo that
  it has ever successfully judged a trace or that judging with a model
  outside `qwen2.5-coder`'s family (the model that generated the fixture
  traces' CODING steps) actually changes the verdict.
- **No bias mitigation beyond "pick a different model family" as an
  intention.** AWS Module 3 names positional, verbosity, and
  self-reinforcement bias explicitly and recommends mitigating them with
  ensembles/multiple judge families and calibration against human ratings.
  None of that exists here even in scaffold form — there's exactly one judge
  prompt, one model parameter, one HTTP call, no ensemble, no calibration.
- **No OpenTelemetry / real tracing infrastructure.** This harness is a
  batch reader of already-complete `.jsonl` files. There's no live span
  collection, no cross-service trace correlation, no equivalent of Bedrock
  AgentCore Evaluations as a managed, always-on service — "evaluation" here
  means "run `mvn test` against files already on disk."
- **No production-derived dataset pipeline.** AWS's fourth dataset type
  (sampled and labeled from live production traffic, addressing the "cold
  start problem" of not having enough real failures to learn from yet) isn't
  built. The golden set is 6 runs, hand-transcribed from one README, not an
  automated sampling/labeling loop.
- **Two fixture directories are present but unused.**
  `src/test/resources/phase4-trace/` and `src/test/resources/phase8-checkpoints/`
  were copied in during setup but are in different JSON schemas than what
  `TraceLoader`/`RunTrace` parse (phase4's `tool`/`iteration`/`status` tool-
  call audit format, and phase8's `CheckpointStore` event vocabulary —
  `run_start`/`plan_generated`/`gate1_waiting`/`escalate` — rather than
  `ObservabilityCollector`'s `step`/`gate_decision`/`test_run`/
  `run_summary` shape). No class in `src/main/java` reads either directory.
  They're left in place rather than deleted so the "don't guess, verify"
  discipline extends to this README too — noted here explicitly instead of
  silently implying every fixture on disk is exercised.
- **`score()`/`classify()` are deterministic by design, not a judge
  substitute.** `EvalHarness` deliberately keeps its CI-gate math LLM-free
  (a CI gate needs a number that doesn't change between two runs over the
  same file) — this is the right call for a threshold gate, but it means the
  "quality" component of trajectory scoring is a hand-tuned weighted formula
  (50/20/20/10/-15 point buckets), not anything a judge model evaluated.
- **No confidence/uncertainty escalation design or input/output guardrail
  filtering** — that's Module 3's "decision engines" territory and overlaps
  with what `phase4-agents/Guardrails` and `phase9-agent-iam` already cover;
  not duplicated here.

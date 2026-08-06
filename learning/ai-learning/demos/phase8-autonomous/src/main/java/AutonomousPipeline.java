import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The capstone build: PLANNING -> GATE1 -> CODING -> TESTING ->
 * (pass: GATE2 -> PR OPEN) / (fail: RETRY up to 3 -> ESCALATE), with
 * rejection at either gate looping back to PLANNING with feedback -- exactly
 * the state machine in openspec/specs/phase8-autonomous/spec.md.
 *
 * Run from THIS directory (demos/phase8-autonomous/):
 *   mvn -q compile exec:java                                   (interactive gates)
 *   mvn -q compile exec:java -Dexec.args="--auto"              (auto-approve both gates)
 *   mvn -q compile exec:java -Dexec.args="--auto --chaos-fail=3"  (force ESCALATE path)
 *
 * --auto refuses to run inside a REAL git repository unless paired with the explicit
 * --i-understand-the-risk flag -- see the safety-rail check at the top of main(). Outside
 * a git repo (this project's current state) --auto is always safe: GitOps/PROpener only
 * ever simulate.
 *
 * See README.md for the full CLI flag list and what each safety rail does.
 */
public class AutonomousPipeline {

    private static final int MAX_STEPS = 20;                 // safety rail: infinite loop
    private static final int DEFAULT_TOKEN_BUDGET = 40_000;   // safety rail: cost explosion
    private static final int MAX_TEST_RETRIES = 3;

    private static final String PLANNER_MODEL = "llama3.2:3b";
    private static final String CODER_MODEL = "qwen2.5-coder:7b";
    private static final String REVIEWER_MODEL = "llama3.2:3b";

    enum State { PLANNING, GATE1, CODING, TESTING, RETRY, ESCALATE, GATE2, PR_OPEN }

    public static void main(String[] args) throws Exception {
        boolean autoApprove = hasFlag(args, "--auto");
        boolean riskAcknowledged = hasFlag(args, "--i-understand-the-risk");
        int chaosFail = intFlag(args, "--chaos-fail", 0);
        int tokenBudget = intFlag(args, "--token-budget", DEFAULT_TOKEN_BUDGET);

        Path moduleRoot = Path.of("").toAbsolutePath();
        GitOps gitOps = new GitOps(moduleRoot);

        // safety rail: --auto exists for testing against a non-repo (see GitOps/PROpener
        // javadoc). The moment this directory becomes a real git repo, --auto would silently
        // skip BOTH human gates for real `git commit`/`gh pr create` actions -- never let that
        // happen by accident. Requires a second, explicit flag to override.
        if (autoApprove && gitOps.isGitRepo() && !riskAcknowledged) {
            System.err.println("[safety-rail] refusing to run: --auto in a REAL git repository would "
                    + "skip both human approval gates for real `git commit` / `gh pr create` actions. "
                    + "Re-run with --auto --i-understand-the-risk if that is genuinely what you want.");
            System.exit(1);
            return;
        }

        // DEMO-001, adapted target -- see README.md "Deviations from spec" for why
        // this points at fixture/ instead of demos/phase1-local-serving/LocalVsHostedDemo.java.
        Ticket ticket = new Ticket(
                "DEMO-001",
                "Add retry logic to OllamaClient",
                "OllamaClient.complete() should retry up to 3 times on HTTP 5xx " +
                        "with exponential backoff (100ms, 200ms, 400ms). Log each retry attempt. " +
                        "IMPORTANT: keep the existing public method name and signature exactly as " +
                        "`public String complete(HttpCall httpCall) throws Exception` -- callers depend " +
                        "on it, do not rename it or change its signature.",
                "fixture/src/main/java/fixture/FixtureOllamaClient.java");

        String runId = ticket.id() + "-" + System.currentTimeMillis();
        Path fixturePomDir = moduleRoot.resolve("fixture");

        OllamaClient client = new OllamaClient();
        LocalMcpFileServer mcp = new LocalMcpFileServer(moduleRoot);
        PlannerAgent planner = new PlannerAgent(client, PLANNER_MODEL, mcp);
        CoderAgent coder = new CoderAgent(client, CODER_MODEL);
        ReviewerAgent reviewer = new ReviewerAgent(client, REVIEWER_MODEL, loadSkill());
        TestRunner testRunner = new TestRunner(fixturePomDir, chaosFail);
        Gate gate = new Gate(autoApprove);
        CheckpointStore checkpoints = new CheckpointStore(runId);
        ObservabilityCollector obs = new ObservabilityCollector(runId, tokenBudget);
        PROpener prOpener = new PROpener(moduleRoot, gitOps);

        System.out.println("=".repeat(70));
        System.out.println("Phase 8 -- Autonomous Pipeline");
        System.out.println("Ticket    : " + ticket.id() + " - " + ticket.title());
        System.out.println("Target    : " + ticket.targetFile());
        System.out.println("Run id    : " + runId);
        System.out.println("Checkpoint: " + checkpoints.path().toAbsolutePath());
        System.out.println("Mode      : " + (autoApprove ? "AUTO-APPROVE (non-interactive)" : "INTERACTIVE (stdin y/n)"));
        if (chaosFail > 0) {
            System.out.println("Chaos     : forcing first " + chaosFail + " test result(s) to FAIL (demo of ESCALATE path)");
        }
        System.out.println("=".repeat(70));

        checkpoints.write("run_start", "ticket", ticket.id(), "title", ticket.title(),
                "timestamp", java.time.Instant.now().toString());

        State state = State.PLANNING;
        int stepCount = 0;
        int testRetries = 0;
        String feedback = null;
        String plan = null;
        String reviewSummary = "";
        GitOps.StashResult stash = null;
        String outcome = "unknown";

        runLoop:
        while (true) {
            stepCount++;
            // safety rail: max step count -- guarantees this loop cannot run forever
            if (stepCount > MAX_STEPS) {
                System.out.println("\n[safety-rail] max step count (" + MAX_STEPS + ") exceeded -- stopping.");
                checkpoints.write("aborted", "reason", "max_step_count_exceeded");
                if (stash != null) {
                    gitOps.restoreStashIfNeeded(stash);
                }
                outcome = "aborted-max-steps";
                break;
            }

            switch (state) {
                case PLANNING -> {
                    System.out.println("\n--- PLANNING (step " + stepCount + ") ---");
                    PlannerAgent.PlanResult pr = planner.plan(ticket, feedback);
                    plan = pr.plan();
                    boolean overBudget = obs.recordStep("planning", pr.tokensIn(), pr.tokensOut(), pr.latencyMs());
                    checkpoints.write("plan_generated", "plan", plan, "step_tokens", pr.tokensIn() + pr.tokensOut());
                    System.out.println(plan);
                    feedback = null;
                    state = overBudget ? State.ESCALATE : State.GATE1;
                    if (overBudget) {
                        feedback = "safety-rail: token budget exceeded during PLANNING";
                    }
                }
                case GATE1 -> {
                    checkpoints.write("gate1_waiting", "gate", "plan_approval");
                    Gate.Decision d = gate.ask("Does this plan look right?");
                    obs.recordGate("gate1", d.approved(), d.feedback());
                    if (d.approved()) {
                        checkpoints.write("gate1_approved", "by", "human");
                        state = State.CODING;
                    } else {
                        checkpoints.write("gate1_rejected", "by", "human", "feedback", d.feedback());
                        feedback = d.feedback();
                        state = State.PLANNING;
                    }
                }
                case CODING -> {
                    System.out.println("\n--- CODING (attempt " + (testRetries + 1) + "/" + MAX_TEST_RETRIES + ") ---");
                    checkpoints.write("coding_start");
                    // safety rail: stash working tree before the FIRST write this run, restore on abort
                    if (stash == null) {
                        stash = gitOps.stashIfPossible();
                    }
                    // re-read current on-disk content rather than trust the plan-time snapshot --
                    // cheap, and correct if anything changed between GATE1 and here
                    String existing = mcp.call("resources/read", ticket.targetFile()).path("text").asText("");
                    String coderInput = buildCoderPrompt(ticket, plan, existing, feedback);
                    TaskResult codeResult = coder.handle(new Task("code.generate", coderInput, plan));
                    boolean overBudget = obs.recordStep("coding", codeResult.tokensIn(), codeResult.tokensOut(), codeResult.latencyMs());

                    if (!codeResult.success()) {
                        checkpoints.write("coding_failed", "issues", String.join(";", codeResult.issues()));
                        System.out.println("[coder] failed: " + codeResult.issues());
                        state = State.ESCALATE;
                        feedback = "coder failed: " + codeResult.issues();
                        break;
                    }

                    String newCode = codeResult.output();
                    mcp.write(ticket.targetFile(), newCode);
                    obs.recordFileWritten(ticket.targetFile(), newCode.length());
                    checkpoints.write("file_written", "path", ticket.targetFile(), "chars", newCode.length());
                    System.out.println("[coder] wrote " + newCode.length() + " chars to " + ticket.targetFile());

                    // safety rail: "unreviewed changes" -- reviewer runs before Gate 2
                    TaskResult reviewResult = reviewer.handle(new Task("code.review", newCode, ticket.description()));
                    obs.recordStep("review", reviewResult.tokensIn(), reviewResult.tokensOut(), reviewResult.latencyMs());
                    reviewSummary = reviewResult.output();
                    checkpoints.write("review_completed", "issues", String.join(";", reviewResult.issues()));
                    System.out.println("[reviewer] " + (reviewResult.issues().isEmpty() ? "no issues found" : reviewResult.issues()));

                    if (overBudget) {
                        state = State.ESCALATE;
                        feedback = "safety-rail: token budget exceeded during CODING";
                    } else {
                        state = State.TESTING;
                    }
                }
                case TESTING -> {
                    System.out.println("\n--- TESTING ---");
                    TestRunner.Result tr = testRunner.run();
                    obs.recordTest(tr.passed(), tr.durationMs());
                    checkpoints.write("test_run", "passed", tr.passed(), "duration_ms", tr.durationMs());
                    System.out.println("[tests] passed=" + tr.passed() + " duration=" + tr.durationMs() + "ms");
                    if (!tr.passed()) {
                        System.out.println(tr.output());
                    }

                    if (tr.passed()) {
                        testRetries = 0;
                        feedback = null;
                        state = State.GATE2;
                    } else {
                        testRetries++;
                        feedback = "Tests FAILED (attempt " + testRetries + "/" + MAX_TEST_RETRIES + "):\n" + tr.output();
                        state = testRetries >= MAX_TEST_RETRIES ? State.ESCALATE : State.RETRY;
                    }
                }
                case RETRY -> {
                    System.out.println("\n--- RETRY " + testRetries + "/" + MAX_TEST_RETRIES + " ---");
                    checkpoints.write("retry", "attempt", testRetries, "reason", "tests_failed");
                    state = State.CODING;
                }
                case ESCALATE -> {
                    System.out.println("\n--- ESCALATE ---");
                    String reason = testRetries >= MAX_TEST_RETRIES
                            ? MAX_TEST_RETRIES + "_consecutive_test_failures"
                            : "pipeline_error_or_budget";
                    checkpoints.write("escalate", "reason", reason, "feedback", feedback == null ? "" : feedback);
                    System.out.println("[ESCALATE] " + reason + " -- notifying human, NOT looping forever. "
                            + "A person needs to look at this ticket manually.");
                    if (stash != null) {
                        gitOps.restoreStashIfNeeded(stash); // roll back: don't leave broken code behind
                    }
                    outcome = "escalated (" + reason + ")";
                    break runLoop;
                }
                case GATE2 -> {
                    checkpoints.write("gate2_waiting", "gate", "pr_approval");
                    Gate.Decision d = gate.ask("Open PR?");
                    obs.recordGate("gate2", d.approved(), d.feedback());
                    if (d.approved()) {
                        checkpoints.write("gate2_approved", "by", "human");
                        state = State.PR_OPEN;
                    } else {
                        checkpoints.write("gate2_rejected", "by", "human", "feedback", d.feedback());
                        feedback = d.feedback();
                        state = State.PLANNING;
                    }
                }
                case PR_OPEN -> {
                    System.out.println("\n--- PR OPEN ---");
                    PROpener.PrResult pr = prOpener.openPr(ticket, ticket.targetFile(), reviewSummary);
                    checkpoints.write("pr_opened", "url", pr.url(), "cost_usd", round(obs.totalCostUsd()),
                            "simulated", pr.simulated());
                    System.out.println("[pr-opener] " + (pr.simulated() ? "SIMULATED" : "REAL") + " -> " + pr.url());
                    outcome = pr.simulated() ? "pr-simulated" : "pr-opened";
                    break runLoop;
                }
            }
        }

        obs.printSummary(outcome);
    }

    private static String buildCoderPrompt(Ticket ticket, String plan, String existing, String feedback) {
        StringBuilder sb = new StringBuilder();
        sb.append("TICKET ").append(ticket.id()).append(": ").append(ticket.title()).append('\n');
        sb.append(ticket.description()).append("\n\n");
        sb.append("APPROVED PLAN:\n").append(plan).append("\n\n");
        sb.append("CURRENT FILE CONTENT (rewrite the COMPLETE file with the fix applied):\n---\n");
        sb.append(existing.isBlank() ? "(new file)" : existing);
        sb.append("\n---\n");
        if (feedback != null && !feedback.isBlank()) {
            sb.append("\nPREVIOUS ATTEMPT FEEDBACK (you MUST fix this):\n").append(feedback).append('\n');
        }
        return sb.toString();
    }

    private static String loadSkill() {
        try {
            Path start = Path.of(AutonomousPipeline.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            for (Path dir = start; dir != null; dir = dir.getParent()) {
                Path candidate = dir.resolve("skills/java-standards/skill.md");
                if (Files.exists(candidate)) {
                    return Files.readString(candidate);
                }
            }
        } catch (Exception e) {
            System.out.println("(could not load java-standards skill: " + e.getMessage() + ")");
        }
        return "(java-standards skill.md not found -- reviewing without it)";
    }

    private static boolean hasFlag(String[] args, String name) {
        for (String a : args) {
            if (a.equals(name)) {
                return true;
            }
        }
        return false;
    }

    private static int intFlag(String[] args, String name, int def) {
        String prefix = name + "=";
        for (String a : args) {
            if (a.startsWith(prefix)) {
                try {
                    return Integer.parseInt(a.substring(prefix.length()));
                } catch (NumberFormatException ignored) {
                    // fall through to default
                }
            }
        }
        return def;
    }

    private static double round(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }
}

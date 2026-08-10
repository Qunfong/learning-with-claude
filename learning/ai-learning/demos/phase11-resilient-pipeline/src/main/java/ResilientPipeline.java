import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Phase 11 -- Resilient Multi-Agent Pipeline (Gap 4: "Production multi-agent
 * failure handling — partially covered, shallow", see
 * learning/ai-learning-gap-review/NOTES.md).
 *
 * Chains PlannerAgent -&gt; CoderAgent -&gt; ReviewerAgent -- the same three-hop
 * shape phase7-multi-agent's Coder/Reviewer pair sketches -- but adds the
 * two mechanisms Gap 4 calls out as missing there: each hop is wrapped in
 * its own named {@link CircuitBreaker}, and each hop's schema-validated
 * {@link TaskResult} (already passed through {@link HandoffValidator} inside
 * the agent itself) is gated on {@code confidence} before being handed to
 * the next agent -- a confidence below {@link #CONFIDENCE_THRESHOLD} makes
 * the pipeline abstain (stop and escalate) rather than blindly forward a
 * result the agent itself wasn't sure about.
 *
 * Three scenarios, run in sequence:
 * <ol>
 *   <li><b>Happy path</b> -- all three hops succeed above the confidence
 *       threshold, end to end.</li>
 *   <li><b>Low-confidence abstention</b> -- ReviewerAgent reports low
 *       confidence on a review of money-moving code; the pipeline stops
 *       there instead of treating it as good input to whatever hop would
 *       come next.</li>
 *   <li><b>Circuit breaker trip</b> -- a chaos-mode CoderAgent fails every
 *       call (simulating a hard-down dependency); after
 *       {@link #CIRCUIT_FAILURE_THRESHOLD} consecutive failures its breaker
 *       opens, and the next call fails fast with NO attempt made at all --
 *       the opposite instinct from {@link Retry}, which tries harder on one
 *       call rather than giving up on the dependency as a whole.</li>
 * </ol>
 *
 * Run (mock mode, no live Ollama needed): {@code mvn -q compile exec:java -Dphase12.mock=true}
 * Live run: start Ollama and pull {@code qwen2.5-coder:7b}, then
 * {@code mvn -q compile exec:java}.
 */
public class ResilientPipeline {

    static final double CONFIDENCE_THRESHOLD = 0.5;
    private static final int CIRCUIT_FAILURE_THRESHOLD = 3;
    private static final Duration CIRCUIT_COOLDOWN = Duration.ofSeconds(5);

    public static void main(String[] args) {
        boolean mock = Boolean.getBoolean("phase12.mock");

        System.out.println("=".repeat(72));
        System.out.println("Phase 11 -- Resilient Multi-Agent Pipeline (Gap 4)");
        if (mock) {
            System.out.println("MODE: mock (-Dphase12.mock=true) -- no live Ollama calls will be made");
        } else {
            System.out.println("MODE: live -- requires Ollama running with qwen2.5-coder:7b pulled");
        }
        System.out.println("=".repeat(72));

        try {
            scenario1HappyPath();
            scenario2Abstention();
            scenario3CircuitBreakerTrip();
        } catch (RuntimeException e) {
            System.out.println("\n" + "=".repeat(72));
            System.out.println("DEMO FAILED -- could not reach Ollama or an unexpected error occurred.");
            System.out.println("Cause: " + e);
            System.out.println("Fix: start Ollama (`ollama serve`) and make sure qwen2.5-coder:7b is");
            System.out.println("pulled -- or rerun with -Dphase12.mock=true to see the same trace");
            System.out.println("without a live model.");
            System.out.println("=".repeat(72));
            throw e;
        }

        System.out.println("\n" + "=".repeat(72));
        System.out.println("Phase 11 demo complete.");
        System.out.println("=".repeat(72));
    }

    private static void scenario1HappyPath() {
        System.out.println("\n--- Scenario 1: happy path (Planner -> Coder -> Reviewer) ---");

        OllamaClient client = new OllamaClient();
        A2AAgent planner = new PlannerAgent(client);
        A2AAgent coder = new CoderAgent(client);
        A2AAgent reviewer = new ReviewerAgent(client);

        CircuitBreaker plannerBreaker = new CircuitBreaker("planner-agent", CIRCUIT_FAILURE_THRESHOLD, CIRCUIT_COOLDOWN);
        CircuitBreaker coderBreaker = new CircuitBreaker("coder-agent", CIRCUIT_FAILURE_THRESHOLD, CIRCUIT_COOLDOWN);
        CircuitBreaker reviewerBreaker = new CircuitBreaker("reviewer-agent", CIRCUIT_FAILURE_THRESHOLD, CIRCUIT_COOLDOWN);

        String featureRequest = "Add a bounds check to the array index parameter.";
        Task planTask = new Task(UUID.randomUUID().toString(), "plan.create", Map.of("featureRequest", featureRequest));
        TaskResult planResult = invoke("PlannerAgent", plannerBreaker, planner, planTask);
        if (!passesConfidenceGate("PlannerAgent", planResult)) {
            return;
        }

        Task codeTask = new Task(UUID.randomUUID().toString(), "code.generate", Map.of(
                "featureDescription", featureRequest,
                "plan", planResult.artifacts().get(0).content()));
        TaskResult codeResult = invoke("CoderAgent", coderBreaker, coder, codeTask);
        if (!passesConfidenceGate("CoderAgent", codeResult)) {
            return;
        }

        Task reviewTask = new Task(UUID.randomUUID().toString(), "code.review",
                Map.of("code", codeResult.artifacts().get(0).content()));
        TaskResult reviewResult = invoke("ReviewerAgent", reviewerBreaker, reviewer, reviewTask);
        if (!passesConfidenceGate("ReviewerAgent", reviewResult)) {
            return;
        }

        System.out.println("Scenario 1 completed end-to-end. Final message: " + reviewResult.message());
    }

    private static void scenario2Abstention() {
        System.out.println("\n--- Scenario 2: low-confidence abstention ---");

        OllamaClient client = new OllamaClient();
        A2AAgent planner = new PlannerAgent(client);
        A2AAgent coder = new CoderAgent(client);
        A2AAgent reviewer = new ReviewerAgent(client, true); // forceLowConfidence

        CircuitBreaker plannerBreaker = new CircuitBreaker("planner-agent-s2", CIRCUIT_FAILURE_THRESHOLD, CIRCUIT_COOLDOWN);
        CircuitBreaker coderBreaker = new CircuitBreaker("coder-agent-s2", CIRCUIT_FAILURE_THRESHOLD, CIRCUIT_COOLDOWN);
        CircuitBreaker reviewerBreaker = new CircuitBreaker("reviewer-agent-s2", CIRCUIT_FAILURE_THRESHOLD, CIRCUIT_COOLDOWN);

        String featureRequest = "Refactor the payment retry logic.";
        Task planTask = new Task(UUID.randomUUID().toString(), "plan.create", Map.of("featureRequest", featureRequest));
        TaskResult planResult = invoke("PlannerAgent", plannerBreaker, planner, planTask);
        if (!passesConfidenceGate("PlannerAgent", planResult)) {
            return;
        }

        Task codeTask = new Task(UUID.randomUUID().toString(), "code.generate", Map.of(
                "featureDescription", featureRequest,
                "plan", planResult.artifacts().get(0).content()));
        TaskResult codeResult = invoke("CoderAgent", coderBreaker, coder, codeTask);
        if (!passesConfidenceGate("CoderAgent", codeResult)) {
            return;
        }

        Task reviewTask = new Task(UUID.randomUUID().toString(), "code.review",
                Map.of("code", codeResult.artifacts().get(0).content()));
        TaskResult reviewResult = invoke("ReviewerAgent", reviewerBreaker, reviewer, reviewTask);
        // expected: ReviewerAgent's low-confidence mock trips the gate here --
        // the pipeline stops rather than presenting this as a clean sign-off
        passesConfidenceGate("ReviewerAgent", reviewResult);
    }

    private static void scenario3CircuitBreakerTrip() {
        System.out.println("\n--- Scenario 3: circuit breaker trip (chaos-mode CoderAgent) ---");

        OllamaClient client = new OllamaClient();
        A2AAgent chaosCoder = new CoderAgent(client, true); // forceChaos -- simulates a hard-down dependency
        CircuitBreaker breaker = new CircuitBreaker("coder-agent-chaos", CIRCUIT_FAILURE_THRESHOLD, CIRCUIT_COOLDOWN);

        int totalCalls = CIRCUIT_FAILURE_THRESHOLD + 1;
        for (int i = 1; i <= totalCalls; i++) {
            Task task = new Task("chaos-task-" + i, "code.generate", Map.of("featureDescription", "chaos"));
            try {
                invoke("CoderAgent(chaos)", breaker, chaosCoder, task);
            } catch (CircuitBreaker.CircuitOpenException e) {
                System.out.println("  call " + i + " -- FAILED FAST, no attempt made: " + e.getMessage());
            } catch (RuntimeException e) {
                System.out.println("  call " + i + " -- attempted and failed: " + e.getMessage()
                        + " [breaker state=" + breaker.state() + ", streak=" + breaker.consecutiveFailures() + "]");
            }
        }
        System.out.println("Final breaker state: " + breaker.state()
                + " (this run's failure streak=" + breaker.consecutiveFailures() + ")");
    }

    private static TaskResult invoke(String agentName, CircuitBreaker breaker, A2AAgent agent, Task task) {
        System.out.println("  -> " + agentName + " handling task " + task.id() + " (type=" + task.type() + ")");
        TaskResult result = breaker.call(() -> {
            try {
                return agent.handle(task);
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        System.out.printf(Locale.ROOT, "  <- %s state=%s confidence=%.2f%n", agentName, result.state(), result.confidence());
        return result;
    }

    /** Package-private (not private) so tests can exercise the confidence
     * gate directly without going through a full Ollama/mock agent call. */
    static boolean passesConfidenceGate(String agentName, TaskResult result) {
        if (result.confidence() < CONFIDENCE_THRESHOLD) {
            System.out.println("  !! " + agentName + " confidence " + result.confidence()
                    + " is below threshold " + CONFIDENCE_THRESHOLD
                    + " -- ABSTAINING: pipeline stops here rather than passing a low-confidence result onward.");
            return false;
        }
        return true;
    }
}

import java.util.List;

/**
 * CoderAgent -- A2A capability {@code code.generate}. Second hop of the
 * Planner -&gt; Coder -&gt; Reviewer chain {@link ResilientPipeline} runs.
 *
 * Also this phase's "deliberately-broken dependency" for the circuit-breaker
 * demonstration: pass {@code chaos=true} to the two-arg constructor (or run
 * the whole JVM with {@code -Dphase12.chaosFail=true}, which the no-arg-style
 * {@link #CoderAgent(OllamaClient)} constructor reads as its default) and
 * every {@link #handle} call throws immediately, before any Ollama call is
 * even attempted -- simulating a dependency that is simply down. Wrapped in
 * its own {@link CircuitBreaker} by {@link ResilientPipeline}, repeated
 * chaos failures trip the breaker open exactly as a real outage would.
 */
class CoderAgent implements A2AAgent {

    private static final String MODEL = "qwen2.5-coder:7b";

    /** Set -Dphase12.mock=true to run the whole demo without a live Ollama
     * call -- see README "Running it". */
    private static final boolean MOCK = Boolean.getBoolean("phase12.mock");

    /** Default chaos setting for the no-arg-style constructor -- see class
     * javadoc. {@link ResilientPipeline}'s chaos scenario instead uses the
     * explicit two-arg constructor so it doesn't depend on a JVM-wide flag. */
    private static final boolean CHAOS_FLAG = Boolean.getBoolean("phase12.chaosFail");

    private static final String SYSTEM_PROMPT = """
            You are CoderAgent, a specialist in implementing small, focused Java
            changes from a plan.

            Respond with ONLY a single JSON object -- no prose before or after, no
            markdown code fence -- matching EXACTLY this shape:
            {
              "taskId": "<echo the task id you were given, verbatim>",
              "state": "DONE" or "FAILED",
              "artifacts": [{"type": "java_file", "content": "<the Java source, as a
                JSON string>"}],
              "issues": [],
              "message": "<one-line summary>",
              "confidence": <number in [0.0, 1.0], your own honest self-assessment>
            }
            """;

    private final OllamaClient client;
    private final boolean chaos;

    CoderAgent(OllamaClient client) {
        this(client, CHAOS_FLAG);
    }

    CoderAgent(OllamaClient client, boolean chaos) {
        this.client = client;
        this.chaos = chaos;
    }

    @Override
    public AgentCard card() {
        return new AgentCard("CoderAgent", List.of("code.generate"), "in-process://coder-agent");
    }

    @Override
    public TaskResult handle(Task task) {
        if (chaos) {
            // simulates a hard-down dependency -- no Ollama call is even attempted,
            // exactly what CircuitBreaker's failure streak should be watching for
            throw new RuntimeException("simulated CoderAgent outage (chaos mode) -- dependency is down");
        }

        String featureDescription = task.input().getOrDefault("featureDescription", "");
        String plan = task.input().getOrDefault("plan", "");

        String raw;
        if (MOCK) {
            System.out.println("  [mock] CoderAgent returning canned completion (phase12.mock=true, no Ollama call)");
            raw = mockResponse(task.id());
        } else {
            String prompt = SYSTEM_PROMPT + "\nFeature: " + featureDescription
                    + "\nPlan:\n" + plan + "\nTask id: " + task.id();
            raw = client.generate(MODEL, prompt);
        }

        return HandoffValidator.parseAndValidate(raw);
    }

    private static String mockResponse(String taskId) {
        return """
                {"taskId":"%s","state":"DONE","artifacts":[{"type":"java_file","content":"class Example { int get(int[] a, int i) { if (i < 0 || i >= a.length) { throw new IndexOutOfBoundsException(\\"index \\" + i); } return a[i]; } }"}],"issues":[],"message":"implemented bounds check","confidence":0.78}
                """.formatted(taskId).strip();
    }
}

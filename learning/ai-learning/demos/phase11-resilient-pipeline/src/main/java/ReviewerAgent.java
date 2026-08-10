import java.util.List;

/**
 * ReviewerAgent -- A2A capability {@code code.review}. Final hop of the
 * Planner -&gt; Coder -&gt; Reviewer chain {@link ResilientPipeline} runs.
 *
 * Also this phase's source of the low-confidence abstention demo: pass
 * {@code forceLowConfidence=true} to the two-arg constructor and the mock
 * response reports a deliberately low {@code confidence} (a reviewer that
 * genuinely isn't sure it caught everything in money-moving code) --
 * {@link ResilientPipeline} checks this after every hop and refuses to
 * treat a low-confidence result as good input to whatever would come next.
 */
class ReviewerAgent implements A2AAgent {

    private static final String MODEL = "qwen2.5-coder:7b";

    /** Set -Dphase12.mock=true to run the whole demo without a live Ollama
     * call -- see README "Running it". */
    private static final boolean MOCK = Boolean.getBoolean("phase12.mock");

    private static final String SYSTEM_PROMPT = """
            You are ReviewerAgent, a specialist in reviewing a small Java change
            for correctness and style.

            Respond with ONLY a single JSON object -- no prose before or after, no
            markdown code fence -- matching EXACTLY this shape:
            {
              "taskId": "<echo the task id you were given, verbatim>",
              "state": "DONE" or "FAILED",
              "artifacts": [{"type": "review", "content": "<review summary>"}],
              "issues": ["<issue 1>", "..."],
              "message": "<one-line summary>",
              "confidence": <number in [0.0, 1.0], your own honest self-assessment of
                how thoroughly you reviewed this -- a review you are not fully sure
                is complete should report a lower number, not a reassuring one>
            }
            """;

    private final OllamaClient client;
    private final boolean forceLowConfidence;

    ReviewerAgent(OllamaClient client) {
        this(client, false);
    }

    ReviewerAgent(OllamaClient client, boolean forceLowConfidence) {
        this.client = client;
        this.forceLowConfidence = forceLowConfidence;
    }

    @Override
    public AgentCard card() {
        return new AgentCard("ReviewerAgent", List.of("code.review"), "in-process://reviewer-agent");
    }

    @Override
    public TaskResult handle(Task task) {
        String code = task.input().getOrDefault("code", "");

        String raw;
        if (MOCK) {
            System.out.println("  [mock] ReviewerAgent returning canned completion (phase12.mock=true, no Ollama call)");
            raw = forceLowConfidence ? lowConfidenceMockResponse(task.id()) : mockResponse(task.id());
        } else {
            String prompt = SYSTEM_PROMPT + "\nCode under review:\n" + code + "\nTask id: " + task.id();
            raw = client.generate(MODEL, prompt);
        }

        return HandoffValidator.parseAndValidate(raw);
    }

    private static String mockResponse(String taskId) {
        return """
                {"taskId":"%s","state":"DONE","artifacts":[{"type":"review","content":"Bounds check looks correct; matches the plan."}],"issues":[],"message":"no blocking issues found","confidence":0.9}
                """.formatted(taskId).strip();
    }

    private static String lowConfidenceMockResponse(String taskId) {
        return """
                {"taskId":"%s","state":"DONE","artifacts":[{"type":"review","content":"This change touches money-moving retry logic; I was not able to trace every retry path with confidence in the context I was given."}],"issues":["possible double-charge on retry after a partial success -- needs a human to trace the transaction log"],"message":"reviewed but not confident this is safe to ship unchecked","confidence":0.35}
                """.formatted(taskId).strip();
    }
}

import java.util.List;

/**
 * PlannerAgent -- A2A capability {@code plan.create}. First hop of the
 * Planner -&gt; Coder -&gt; Reviewer chain {@link ResilientPipeline} runs.
 *
 * Its raw model output is routed through
 * {@link HandoffValidator#parseAndValidate} before {@link ResilientPipeline}
 * ever sees a {@link TaskResult} object -- the schema-validated-handoff
 * boundary {@link HandoffValidator}'s own javadoc names this class for.
 * Malformed output (missing field, wrong type, bad enum, confidence out of
 * range) is a hard {@link SchemaValidationException}, never silently
 * coerced.
 */
class PlannerAgent implements A2AAgent {

    private static final String MODEL = "qwen2.5-coder:7b";

    /** Set -Dphase12.mock=true to run the whole demo without a live Ollama
     * call -- see README "Running it". */
    private static final boolean MOCK = Boolean.getBoolean("phase12.mock");

    private static final String SYSTEM_PROMPT = """
            You are PlannerAgent, a specialist in breaking a feature request into
            a short, concrete implementation plan.

            Respond with ONLY a single JSON object -- no prose before or after, no
            markdown code fence -- matching EXACTLY this shape:
            {
              "taskId": "<echo the task id you were given, verbatim>",
              "state": "DONE" or "FAILED",
              "artifacts": [{"type": "plan", "content": "<the plan, numbered steps>"}],
              "issues": [],
              "message": "<one-line summary>",
              "confidence": <number in [0.0, 1.0], your own honest self-assessment of
                how confident you are this plan is correct and complete -- do not
                default to a high number; a genuinely uncertain plan should say so>
            }
            """;

    private final OllamaClient client;

    PlannerAgent(OllamaClient client) {
        this.client = client;
    }

    @Override
    public AgentCard card() {
        return new AgentCard("PlannerAgent", List.of("plan.create"), "in-process://planner-agent");
    }

    @Override
    public TaskResult handle(Task task) {
        String featureRequest = task.input().getOrDefault("featureRequest", "");

        String raw;
        if (MOCK) {
            System.out.println("  [mock] PlannerAgent returning canned completion (phase12.mock=true, no Ollama call)");
            raw = mockResponse(task.id());
        } else {
            String prompt = SYSTEM_PROMPT + "\nFeature request: " + featureRequest + "\nTask id: " + task.id();
            raw = client.generate(MODEL, prompt);
        }

        return HandoffValidator.parseAndValidate(raw);
    }

    private static String mockResponse(String taskId) {
        return """
                {"taskId":"%s","state":"DONE","artifacts":[{"type":"plan","content":"1. Validate the index argument is within array bounds\\n2. Throw IndexOutOfBoundsException on violation\\n3. Add a unit test for the boundary case"}],"issues":[],"message":"plan created","confidence":0.82}
                """.formatted(taskId).strip();
    }
}

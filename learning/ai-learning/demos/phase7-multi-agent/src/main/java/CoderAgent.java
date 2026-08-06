import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CoderAgent — A2A capability {@code code.generate}.
 *
 * Reuses Phase 4's {@link AgentLoop} engine ({@link OllamaClient} +
 * {@link Retry} + {@link Guardrails}) with an EMPTY tool set. That is a
 * deliberate choice, not laziness: "rewrite this file to add feature X" is a
 * single generate-and-return step, not a multi-step tool-use task, so the
 * loop runs exactly one iteration in practice. What matters for the Phase 7
 * learning point is that it is the SAME engine class {@code CodingAgentDemo}
 * (Phase 4) uses, configured only with a different system prompt — exactly
 * that phase's "behavior differs by config, not code" principle, now
 * carried into a multi-agent role.
 *
 * A real extension exercise: give this agent a {@code validate_syntax} tool
 * (brace-balance / javac -Xlint check) and a guardrail-bounded retry loop
 * so it can react to its own compile errors — Phase 4's README documents
 * exactly why that is harder than it sounds (small local models routinely
 * fail to call tools reliably, or fabricate having called them).
 */
class CoderAgent implements A2AAgent {

    private static final String MODEL = "qwen2.5-coder:7b";
    private static final Pattern CODE_FENCE = Pattern.compile("```(?:java)?\\s*([\\s\\S]*?)```");

    /** Set -Dphase7.mock=true to run the whole demo without a live Ollama call —
     * see README "Fallback / mock mode". */
    private static final boolean MOCK = Boolean.getBoolean("phase7.mock");

    private static final String SYSTEM_PROMPT = """
            You are CoderAgent, a specialist in implementing small, focused Java changes.
            You receive a feature request and the FULL existing content of one Java file.
            Rewrite that file to implement the request. Rules:
            - Return ONLY the complete, compilable Java source for the file, wrapped in a
              single ```java code fence.
            - Preserve the existing class name and unrelated methods; change only what the
              feature request requires.
            - No prose before or after the code fence.
            """;

    private static final String MOCK_COMPLETION = """
            ```java
            import com.fasterxml.jackson.databind.ObjectMapper;
            import com.fasterxml.jackson.databind.node.ObjectNode;

            import java.io.IOException;
            import java.net.URI;
            import java.net.http.HttpClient;
            import java.net.http.HttpRequest;
            import java.net.http.HttpResponse;
            import java.time.Duration;

            public class OllamaClient {

                private static final int MAX_ATTEMPTS = 3;
                private static final long BASE_DELAY_MS = 300;
                private static final ObjectMapper JSON = new ObjectMapper();

                private final HttpClient http = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(10))
                        .build();
                private final String baseUrl;

                public OllamaClient(String baseUrl) {
                    this.baseUrl = baseUrl;
                }

                public String complete(String model, String prompt) throws IOException, InterruptedException {
                    RuntimeException lastFailure = null;
                    for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
                        try {
                            return attemptComplete(model, prompt);
                        } catch (IOException e) {
                            lastFailure = new RuntimeException(e);
                            if (attempt == MAX_ATTEMPTS) {
                                throw e;
                            }
                            long delay = BASE_DELAY_MS * (1L << (attempt - 1));
                            Thread.sleep(delay);
                        }
                    }
                    throw lastFailure;
                }

                private String attemptComplete(String model, String prompt) throws IOException, InterruptedException {
                    ObjectNode body = JSON.createObjectNode();
                    body.put("model", model);
                    body.put("prompt", prompt);
                    body.put("stream", false);

                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(baseUrl + "/api/generate"))
                            .timeout(Duration.ofMinutes(2))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)))
                            .build();

                    HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
                    if (response.statusCode() != 200) {
                        throw new IOException("Ollama HTTP " + response.statusCode() + ": " + response.body());
                    }
                    return response.body();
                }
            }
            ```
            """;

    private final AgentLoop loop;

    CoderAgent(OllamaClient client) {
        this.loop = new AgentLoop(client, MODEL, SYSTEM_PROMPT, List.of(),
                new Guardrails(3, 0, true, null));
    }

    @Override
    public AgentCard card() {
        return new AgentCard("CoderAgent", List.of("code.generate"), "in-process://coder-agent");
    }

    @Override
    public TaskResult handle(Task task) {
        if (!"code.generate".equals(task.type())) {
            return TaskResult.failed(task.id(), "CoderAgent cannot handle task type '" + task.type() + "'");
        }
        String featureDescription = task.input().getOrDefault("featureDescription", "");
        String existingCode = task.input().getOrDefault("existingCode", "");
        String fileName = task.input().getOrDefault("fileName", "Generated.java");

        String raw;
        if (MOCK) {
            System.out.println("  [mock] CoderAgent returning canned completion (phase7.mock=true, no Ollama call)");
            raw = MOCK_COMPLETION;
        } else {
            String userTask = """
                    Feature request: %s

                    Existing file (%s):
                    ```java
                    %s
                    ```

                    Rewrite the file to implement the feature request.
                    """.formatted(featureDescription, fileName, existingCode);
            raw = loop.run(userTask);
        }

        if (raw == null || raw.isBlank()) {
            return TaskResult.failed(task.id(), "CoderAgent produced no output (guardrail tripped or empty response)");
        }

        String code = extractCode(raw);
        Artifact artifact = new Artifact("java_file", code);
        return TaskResult.done(task.id(), List.of(artifact), "generated " + fileName);
    }

    static String extractCode(String raw) {
        Matcher m = CODE_FENCE.matcher(raw);
        if (m.find()) {
            return m.group(1).strip();
        }
        return raw.strip();
    }
}

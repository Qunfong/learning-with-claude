import java.util.List;

/**
 * CoderAgent — A2A capability {@code code.generate}. Copied/adapted from
 * {@code phase7-multi-agent/src/main/java/CoderAgent.java}.
 *
 * DEVIATION from phase7: phase7's CoderAgent runs a full {@code AgentLoop}
 * against a live (or {@code -Dphase7.mock=true} canned) Ollama call. This
 * phase's load-bearing deliverable is the A2A **transport and discovery**
 * layer (Part A), not another LLM-inference demo — phase0-8 already cover
 * that repeatedly. So {@code handle()} here is intentionally
 * mock-only/deterministic (no {@code OllamaClient}, no live network call to
 * an LLM), which also keeps {@code mvn test} hermetic (no Ollama
 * dependency). What IS real and load-bearing is the HTTP server this class
 * now owns: {@link #card()} is backed by a live {@link AgentCardServer}
 * request/response, not an in-process string.
 */
class CoderAgent implements A2AAgent {

    private final AgentCardServer cardServer = new AgentCardServer("CoderAgent", List.of("code.generate"));

    void start() {
        cardServer.start();
    }

    void stop() {
        cardServer.stop();
    }

    String cardUrl() {
        return cardServer.cardUrl();
    }

    @Override
    public AgentCard card() {
        return cardServer.card();
    }

    @Override
    public TaskResult handle(Task task) {
        if (!"code.generate".equals(task.type())) {
            return TaskResult.failed(task.id(), "CoderAgent cannot handle task type '" + task.type() + "'");
        }
        String featureDescription = task.input().getOrDefault("featureDescription", "");
        String fileName = task.input().getOrDefault("fileName", "Generated.java");
        // Mock generation — see class javadoc for why this isn't a live LLM call here.
        String code = "// mock CoderAgent output for: " + featureDescription + "\npublic class "
                + fileName.replace(".java", "") + " { }";
        return TaskResult.done(task.id(), List.of(new Artifact("java_file", code)), "generated " + fileName);
    }
}

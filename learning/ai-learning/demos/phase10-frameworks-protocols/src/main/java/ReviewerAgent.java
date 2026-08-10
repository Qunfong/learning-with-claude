import java.util.List;

/**
 * ReviewerAgent — A2A capability {@code code.review}. Copied/adapted from
 * {@code phase7-multi-agent/src/main/java/ReviewerAgent.java}.
 *
 * DEVIATION from phase7: phase7's ReviewerAgent runs a real static regex
 * scan PLUS a live/mocked LLM review pass (needs {@code skills/java-standards
 * /skill.md} on disk and an {@code OllamaClient}). Per {@link CoderAgent}'s
 * javadoc, this module's job is the discovery/transport layer, so the LLM
 * pass and skill-file dependency are dropped and {@code handle()} is a
 * small deterministic mock. What's real here is the same thing as
 * CoderAgent: a live embedded {@link AgentCardServer} answering
 * {@code GET /.well-known/agent-card.json} over an actual socket.
 */
class ReviewerAgent implements A2AAgent {

    private final AgentCardServer cardServer = new AgentCardServer("ReviewerAgent", List.of("code.review"));

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
        if (!"code.review".equals(task.type())) {
            return TaskResult.failed(task.id(), "ReviewerAgent cannot handle task type '" + task.type() + "'");
        }
        String code = task.input().getOrDefault("code", "");
        if (code.isBlank()) {
            return TaskResult.failed(task.id(), "ReviewerAgent received no code to review");
        }
        // Mock review — see class javadoc for why this isn't the real R1-R10 scan here.
        return new TaskResult(task.id(), TaskState.DONE, List.of(new Artifact("java_file_annotated", code)),
                List.of("[mock] no violations found"), "mock review complete");
    }
}

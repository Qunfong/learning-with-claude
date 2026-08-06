import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * OrchestratorAgent — thin coordinator, the "hierarchical" topology from
 * spec.md (Orchestrator -> [CoderAgent, ReviewerAgent], run SEQUENTIALLY
 * because ReviewerAgent needs CoderAgent's output — this is not a
 * parallelizable fan-out). It owns no LLM logic of its own: it reads the
 * file the ticket concerns (MCP-style, see {@link McpStyleFileReader}),
 * builds a {@link Task} for CoderAgent, waits for its artifact, builds a
 * {@link Task} for ReviewerAgent from that artifact, and returns the
 * combined result.
 *
 * Agent discovery here is the simplest possible answer to spec.md's open
 * question #3: the orchestrator is constructed with direct references to
 * both agents (a compile-time registry). Real A2A answers the same question
 * with Agent Cards served from a URL — a runtime service registry. Both are
 * "the orchestrator knows what agents exist and what they can do"; only the
 * WHEN (compile-time vs. runtime) and WHERE (same process vs. network)
 * differ.
 */
class OrchestratorAgent implements A2AAgent {

    private final A2AAgent coder;
    private final A2AAgent reviewer;

    OrchestratorAgent(A2AAgent coder, A2AAgent reviewer) {
        this.coder = coder;
        this.reviewer = reviewer;
    }

    @Override
    public AgentCard card() {
        return new AgentCard("OrchestratorAgent", List.of("feature.delegate"), "in-process://orchestrator");
    }

    @Override
    public TaskResult handle(Task task) throws Exception {
        String ticket = task.input().getOrDefault("ticket", "");
        String filePath = task.input().getOrDefault("filePath", "");
        String fileName = Path.of(filePath).getFileName().toString();

        System.out.println("[orchestrator] agent registry (compile-time, in-process):");
        System.out.println("  - " + coder.card());
        System.out.println("  - " + reviewer.card());

        System.out.println("\n[orchestrator] reading " + fileName + " (MCP-style resources/read)...");
        ObjectNode resource = McpStyleFileReader.readResource("demo://phase7/" + fileName, Path.of(filePath));
        String existingCode = resource.path("text").asText();
        System.out.println("  -> " + existingCode.split("\n", -1).length + " lines read from " + filePath);

        System.out.println("\n[orchestrator] delegating to CoderAgent (code.generate)...");
        Task codeTask = new Task("t-" + task.id() + "-code", "code.generate",
                Map.of("featureDescription", ticket, "existingCode", existingCode, "fileName", fileName));
        TaskResult codeResult = coder.handle(codeTask);
        if (codeResult.state() != TaskState.DONE || codeResult.artifacts().isEmpty()) {
            return TaskResult.failed(task.id(), "CoderAgent failed: " + codeResult.message());
        }
        String generatedCode = codeResult.artifacts().get(0).content();
        System.out.println("  -> CoderAgent: " + codeResult.message());

        System.out.println("\n[orchestrator] delegating to ReviewerAgent (code.review)...");
        Task reviewTask = new Task("t-" + task.id() + "-review", "code.review",
                Map.of("code", generatedCode, "fileName", fileName));
        TaskResult reviewResult = reviewer.handle(reviewTask);
        if (reviewResult.state() != TaskState.DONE) {
            return TaskResult.failed(task.id(), "ReviewerAgent failed: " + reviewResult.message());
        }
        System.out.println("  -> ReviewerAgent: " + reviewResult.message());

        return new TaskResult(task.id(), TaskState.DONE, reviewResult.artifacts(),
                reviewResult.issues(), "feature delivered: " + ticket);
    }
}

import java.util.Map;
import java.util.UUID;

/**
 * Phase 7 — Multi-Agent & A2A demo (Option B: in-process {@link A2AAgent}).
 *
 * Scenario (see spec.md "Demo Scenario"): ticket
 * "Add exponential backoff retry to OllamaClient.complete()".
 *
 * Message flow:
 * <pre>
 *   User ticket
 *      |
 *      v
 *   OrchestratorAgent  --reads context/OllamaClient.java (MCP-style)-->
 *      |
 *      v  Task(code.generate)
 *   CoderAgent  --Phase 4 AgentLoop-->  Artifact(java_file)
 *      |
 *      v  Task(code.review)
 *   ReviewerAgent  --java-standards skill + LLM review-->  Artifact(java_file_annotated) + issues[]
 *      |
 *      v
 *   OrchestratorAgent presents final code + review summary
 * </pre>
 *
 * Run: {@code mvn -q compile exec:java}
 * Mock mode (no Ollama needed): {@code mvn -q compile exec:java -Dphase7.mock=true}
 */
public class OrchestratorDemo {

    public static void main(String[] args) throws Exception {
        boolean mock = Boolean.getBoolean("phase7.mock");

        System.out.println("=".repeat(72));
        System.out.println("Phase 7 -- Multi-Agent & A2A (Option B: in-process A2AAgent)");
        if (mock) {
            System.out.println("MODE: mock (-Dphase7.mock=true) -- no live Ollama calls will be made");
        }
        System.out.println("=".repeat(72));

        OllamaClient client = new OllamaClient();

        A2AAgent coder = new CoderAgent(client);
        A2AAgent reviewer = new ReviewerAgent(client);
        OrchestratorAgent orchestrator = new OrchestratorAgent(coder, reviewer);

        String ticket = "Add exponential backoff retry to OllamaClient.complete().";
        String filePath = "context/OllamaClient.java";

        System.out.println("\nTicket : " + ticket);
        System.out.println("Target : " + filePath);

        Task task = new Task(UUID.randomUUID().toString(), "feature.request",
                Map.of("ticket", ticket, "filePath", filePath));

        TaskResult result;
        try {
            result = orchestrator.handle(task);
        } catch (Exception e) {
            System.out.println("\n" + "=".repeat(72));
            System.out.println("DEMO FAILED -- could not reach Ollama or an unexpected error occurred.");
            System.out.println("Cause: " + e);
            System.out.println("Fix: start Ollama (`ollama serve`, or the Ollama app) and make sure");
            System.out.println("qwen2.5-coder:7b and llama3.2:3b are pulled -- or rerun with");
            System.out.println("  mvn -q compile exec:java -Dphase7.mock=true");
            System.out.println("to see the same message flow without a live model.");
            System.out.println("=".repeat(72));
            throw e;
        }

        System.out.println("\n" + "=".repeat(72));
        System.out.println("FINAL RESULT -- state=" + result.state());
        System.out.println("=".repeat(72));

        if (result.state() == TaskState.DONE && !result.artifacts().isEmpty()) {
            System.out.println("\n--- Final annotated code ---\n");
            System.out.println(result.artifacts().get(0).content());
        }

        System.out.println("\n--- Review summary (" + result.issues().size() + " item(s)) ---");
        for (String issue : result.issues()) {
            System.out.println("  - " + issue);
        }
        System.out.println("\n" + result.message());
    }
}

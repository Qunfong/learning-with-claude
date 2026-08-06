import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.List;

/**
 * Copied from Phase 4 (demos/phase4-agents/src/main/java/AgentLoop.java) —
 * the generic plan -> act -> observe -> repeat loop, unchanged. This is the
 * exact engine {@link CoderAgent} reuses per the Phase 7 spec ("CoderAgent
 * ... Calls: Phase 4 agent loop"): same class, different system prompt and
 * tool subset, zero changes to the engine itself — Phase 4's whole point
 * ("behavior differs by config, not code") carries over unchanged to a
 * multi-agent setting.
 */
class AgentLoop {

    private static final ObjectMapper JSON = new ObjectMapper();

    // ---- observability: every tool call logged structurally (JSONL) --------
    private static final Path TRACE_FILE = Path.of("trace.jsonl");

    private static void trace(int iteration, String tool, JsonNode args, String status, long latencyMs, String detail) {
        ObjectNode line = JSON.createObjectNode();
        line.put("ts", Instant.now().toString());
        line.put("iteration", iteration);
        line.put("tool", tool);
        line.set("args", args);
        line.put("status", status); // ok | error | denied | guardrail
        line.put("latencyMs", latencyMs);
        line.put("detail", detail == null ? "" : (detail.length() <= 300 ? detail : detail.substring(0, 300) + "..."));
        try {
            Files.writeString(TRACE_FILE, line + System.lineSeparator(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception e) {
            System.out.println("(could not write trace: " + e.getMessage() + ")");
        }
    }

    /** Pure demo-runner emergency stop, NOT a guardrail feature (that's {@link Guardrails}).
     * Without this rail a truly unguarded loop (Guardrails.none()) against a live model could
     * literally run forever — fine as a learning point, not fine to actually let a process hang. */
    private static final int RUNAWAY_SAFETY_CEILING = 25;

    private final OllamaClient client;
    private final String model;
    private final String systemPrompt;
    private final List<Tool> tools;
    private final Guardrails guardrails;

    AgentLoop(OllamaClient client, String model, String systemPrompt, List<Tool> tools, Guardrails guardrails) {
        this.client = client;
        this.model = model;
        this.systemPrompt = systemPrompt;
        this.tools = tools;
        this.guardrails = guardrails;
    }

    String run(String userTask) {
        ArrayNode messages = JSON.createArrayNode();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.addObject().put("role", "system").put("content", systemPrompt);
        }
        messages.addObject().put("role", "user").put("content", userTask);
        return runLoop(messages);
    }

    private ArrayNode toolsSchema() {
        ArrayNode arr = JSON.createArrayNode();
        for (Tool t : tools) arr.add(t.schema(JSON));
        return arr;
    }

    private Tool find(String name) {
        return tools.stream().filter(t -> t.name().equals(name)).findFirst().orElse(null);
    }

    private String runLoop(ArrayNode messages) {
        String lastCallKey = null;
        int iteration = 0;
        int tokensUsed = 0;

        while (true) {
            iteration++;

            if (guardrails.maxIterations() > 0 && iteration > guardrails.maxIterations()) {
                System.out.println("  [guardrail] max iterations (" + guardrails.maxIterations() + ") reached — loop stopped");
                trace(iteration, "_loop_", JSON.createObjectNode(), "guardrail", 0,
                        "max-iterations (" + guardrails.maxIterations() + ") reached");
                return null;
            }
            if (iteration > RUNAWAY_SAFETY_CEILING) {
                System.out.println("  [demo-runner emergency stop] " + RUNAWAY_SAFETY_CEILING + " iterations reached without " +
                        "guardrail config — in a truly unguarded system this would just keep going. We stop here " +
                        "purely so this demo doesn't actually run forever.");
                return null;
            }

            OllamaClient.ChatResult result = client.chat(model, messages, toolsSchema());
            tokensUsed += result.tokensIn() + result.tokensOut();
            System.out.printf("  (iteration %d, tokens in=%d out=%d, cumulative=%d)%n",
                    iteration, result.tokensIn(), result.tokensOut(), tokensUsed);

            if (guardrails.tokenBudget() > 0 && tokensUsed > guardrails.tokenBudget()) {
                System.out.println("  [guardrail] token budget (" + guardrails.tokenBudget() + ") exceeded — loop stopped");
                trace(iteration, "_loop_", JSON.createObjectNode(), "guardrail", 0,
                        "token-budget (" + guardrails.tokenBudget() + ") exceeded at " + tokensUsed + " tokens");
                return null;
            }

            JsonNode assistantMsg = result.message();
            JsonNode toolCalls = assistantMsg.path("tool_calls");
            if (!toolCalls.isArray() || toolCalls.isEmpty()) {
                return assistantMsg.path("content").asText().strip();
            }

            messages.add(assistantMsg);
            for (JsonNode call : toolCalls) {
                String name = call.path("function").path("name").asText();
                JsonNode args = call.path("function").path("arguments");
                String callKey = name + "(" + args + ")";

                if (guardrails.loopDetection() && callKey.equals(lastCallKey)) {
                    System.out.println("  [guardrail] loop detected: identical tool call twice in a row " +
                            "(" + callKey + ") — loop stopped");
                    trace(iteration, name, args, "guardrail", 0, "loop-detection: identical call repeated");
                    return null;
                }
                lastCallKey = callKey;

                long t0 = System.nanoTime();
                String toolResult = executeWithGuardrails(name, args);
                long ms = (System.nanoTime() - t0) / 1_000_000;
                System.out.println("  [tool] " + callKey + " -> " + toolResult);
                String status = toolResult.startsWith("ERROR:") ? "error"
                        : toolResult.startsWith("DENIED:") ? "denied" : "ok";
                trace(iteration, name, args, status, ms, toolResult);
                messages.addObject().put("role", "tool").put("content", toolResult);
            }
        }
    }

    private String executeWithGuardrails(String name, JsonNode args) {
        Tool tool = find(name);
        if (tool == null) {
            return "ERROR: unknown tool '" + name + "'";
        }
        if (tool.destructive() && guardrails.confirmHook() != null) {
            boolean confirmed = guardrails.confirmHook().confirm(name, args);
            if (!confirmed) {
                System.out.println("  [guardrail] confirm-hook blocked destructive tool '" + name + "'");
                return "DENIED: confirm-hook did not approve this destructive call";
            }
        }
        try {
            return tool.executor().execute(args);
        } catch (IllegalArgumentException e) {
            return "ERROR: " + e.getMessage();
        }
    }
}

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

import java.util.List;

/**
 * Simplified plan -> act -> observe -> repeat loop, adapted from
 * phase4-agents/AgentLoop.java. Trimmed for this phase: no trace.jsonl file
 * (console logging is sufficient evidence for this phase's scenarios, and
 * writing one shared trace.jsonl from two agents running side by side in the
 * same process would interleave confusingly) -- see the README's
 * "Deviations from plan" section. The engine-level guardrails
 * (maxIterations/loopDetection) are still enforced, unchanged from phase4.
 */
class AgentLoop {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int RUNAWAY_SAFETY_CEILING = 15;

    private final OllamaClient client;
    private final String model;
    private final String systemPrompt;
    private final List<Tool> tools;
    private final Guardrails guardrails;
    private final String label;

    AgentLoop(OllamaClient client, String model, String systemPrompt, List<Tool> tools,
              Guardrails guardrails, String label) {
        this.client = client;
        this.model = model;
        this.systemPrompt = systemPrompt;
        this.tools = tools;
        this.guardrails = guardrails;
        this.label = label;
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

        while (true) {
            iteration++;

            if (guardrails.maxIterations() > 0 && iteration > guardrails.maxIterations()) {
                System.out.println("  [" + label + "][guardrail] max iterations (" + guardrails.maxIterations() + ") reached -- loop stopped");
                return null;
            }
            if (iteration > RUNAWAY_SAFETY_CEILING) {
                System.out.println("  [" + label + "][safety-ceiling] " + RUNAWAY_SAFETY_CEILING + " iterations without stopping -- demo-runner stop only");
                return null;
            }

            OllamaClient.ChatResult result = client.chat(model, messages, toolsSchema());
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
                    System.out.println("  [" + label + "][guardrail] loop detected: identical tool call twice in a row (" + callKey + ") -- stopped");
                    return null;
                }
                lastCallKey = callKey;

                String toolResult = executeWithGuardrails(name, args);
                System.out.println("  [" + label + "][tool] " + callKey + " -> " + toolResult);
                messages.addObject().put("role", "tool").put("content", toolResult);
            }
        }
    }

    private String executeWithGuardrails(String name, JsonNode args) {
        Tool tool = find(name);
        if (tool == null) {
            return "FOUT: unknown tool '" + name + "'";
        }
        if (tool.destructive() && guardrails.confirmHook() != null) {
            boolean confirmed = guardrails.confirmHook().confirm(name, args);
            if (!confirmed) {
                System.out.println("  [" + label + "][guardrail] confirm-hook blocked destructive tool '" + name + "'");
                return "DENIED: confirm-hook did not approve this destructive call";
            }
        }
        try {
            return tool.executor().execute(args);
        } catch (IllegalArgumentException e) {
            return "FOUT: " + e.getMessage();
        }
    }
}

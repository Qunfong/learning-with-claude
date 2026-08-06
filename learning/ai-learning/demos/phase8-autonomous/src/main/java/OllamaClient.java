import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Rewrapped from {@code phase4-agents/OllamaClient.java}, simplified for this
 * phase's needs: every agent here (Planner/Coder/Reviewer) does a single-shot
 * chat completion, none of them need the tool-calling loop.
 *
 * WHY no tool-calling here, unlike phase4's version: phase4's own
 * CodingAgentDemo/README documents that small local models are unreliable at
 * tool-calling once an argument gets large (a whole file's content) -- of
 * three real runs, one produced mangled escaped content and two fabricated a
 * "write_file" call in prose without ever invoking it. CoderAgent below asks
 * for the new file content as the plain completion text instead (same
 * approach as phase5-skills/SkillsDemo), which sidesteps that exact failure
 * mode. PlannerAgent's file read is a deterministic fetch (see
 * LocalMcpFileServer) that doesn't need the model to decide when to call it.
 */
class OllamaClient {

    static final ObjectMapper JSON = new ObjectMapper();

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final String baseUrl;

    OllamaClient() {
        this("http://localhost:11434");
    }

    OllamaClient(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    record ChatResult(String text, int tokensIn, int tokensOut) {
    }

    ChatResult chat(String model, String systemPrompt, String userMessage) {
        ObjectNode body = JSON.createObjectNode();
        body.put("model", model);
        body.put("stream", false);

        ArrayNode messages = body.putArray("messages");
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.addObject().put("role", "system").put("content", systemPrompt);
        }
        messages.addObject().put("role", "user").put("content", userMessage);
        // lower temperature for agentic/structured output, same rationale as phase4
        body.putObject("options").put("temperature", 0.2);

        return Retry.withBackoff(3, 300, () -> {
            try {
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/api/chat"))
                        .timeout(Duration.ofMinutes(3))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)))
                        .build();

                HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() >= 500) {
                    throw new Retry.TransientFailure("Ollama HTTP " + resp.statusCode());
                }
                if (resp.statusCode() != 200) {
                    throw new RuntimeException("Ollama HTTP " + resp.statusCode() + ": " + resp.body());
                }

                JsonNode root = JSON.readTree(resp.body());
                int tokensIn = root.path("prompt_eval_count").asInt(0);
                int tokensOut = root.path("eval_count").asInt(0);
                String text = root.path("message").path("content").asText("").strip();
                return new ChatResult(text, tokensIn, tokensOut);
            } catch (IOException e) {
                throw new Retry.TransientFailure("network error: " + e, e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        });
    }
}

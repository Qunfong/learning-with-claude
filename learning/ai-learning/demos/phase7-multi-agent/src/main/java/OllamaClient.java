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
 * Copied from Phase 4 (demos/phase4-agents/src/main/java/OllamaClient.java)
 * so this module has no cross-module Maven dependency — same rationale as
 * {@link Retry}: a raw {@code HttpClient} + Jackson wrapper against Ollama's
 * {@code /api/chat}, shared by every agent in this demo (CoderAgent,
 * ReviewerAgent both hold one instance).
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

    record ChatResult(JsonNode message, int tokensIn, int tokensOut) {}

    ChatResult chat(String model, ArrayNode messages, ArrayNode tools) {
        ObjectNode body = JSON.createObjectNode();
        body.put("model", model);
        body.put("stream", false);
        body.set("messages", messages);
        if (tools != null && !tools.isEmpty()) {
            body.set("tools", tools);
        }
        // lower temperature for agentic / structured-output use: consistent, precise
        // answers (full file rewrites, rule checks), not creativity
        body.putObject("options").put("temperature", 0.2);

        // retry-with-backoff ONLY for transient failures (timeout, 5xx) -- a 4xx (e.g. bad
        // model request) is a permanent failure and is NOT retried, see Retry.TransientFailure
        return Retry.withBackoff(3, 300, () -> {
            try {
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/api/chat"))
                        .timeout(Duration.ofMinutes(2))
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
                return new ChatResult(root.path("message"), tokensIn, tokensOut);
            } catch (IOException e) {
                throw new Retry.TransientFailure("network error: " + e, e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        });
    }
}

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
 * Deliberately smaller than phase4-agents/OllamaClient: same raw HttpClient +
 * Jackson call against Ollama's {@code /api/chat}, but WITHOUT phase4's
 * Retry.withBackoff wrapper. Retry-on-transient-failure is phase4's lesson
 * (see its README, section on GuardrailsDemo/CodingAgentDemo); it is
 * orthogonal to this phase's lesson (the four levers of domain
 * specialization), so it is intentionally not copied in here — see the
 * README's "Deviations from plan" section.
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
        // low temperature for agentic tool-use: consistent, precise arguments,
        // not creativity -- same rationale as phase4-agents/OllamaClient
        body.putObject("options").put("temperature", 0.2);

        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/chat"))
                    .timeout(Duration.ofMinutes(2))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)))
                    .build();

            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                throw new RuntimeException("Ollama HTTP " + resp.statusCode() + ": " + resp.body());
            }

            JsonNode root = JSON.readTree(resp.body());
            int tokensIn = root.path("prompt_eval_count").asInt(0);
            int tokensOut = root.path("eval_count").asInt(0);
            return new ChatResult(root.path("message"), tokensIn, tokensOut);
        } catch (IOException e) {
            throw new RuntimeException("network error talking to Ollama: " + e, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }
}

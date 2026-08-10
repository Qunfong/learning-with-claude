import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Raw {@code HttpClient} + Jackson wrapper against Ollama's
 * {@code /api/generate} -- same shape as phase7-multi-agent/CoderAgent's
 * embedded {@code OllamaClient} example, kept self-contained per this
 * repo's "no cross-module imports" convention (a POST of
 * {@code {model, prompt, stream:false}}, non-200 throws).
 *
 * The actual HTTP call is wrapped in {@link Retry#withBackoff} -- retrying
 * ONE transient call. That is a different concern from {@link CircuitBreaker},
 * which sits one level up in {@link ResilientPipeline}, around a whole
 * agent's {@code handle()} call, and watches a streak of failures across
 * MANY calls rather than retrying a single one.
 */
class OllamaClient {

    private static final ObjectMapper JSON = new ObjectMapper();

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

    /** Calls {@code /api/generate} and returns the raw "response" text field. */
    String generate(String model, String prompt) {
        return Retry.withBackoff(3, 300, () -> {
            try {
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
                if (response.statusCode() >= 500) {
                    // transient -- Ollama itself errored, worth retrying
                    throw new Retry.TransientFailure("Ollama HTTP " + response.statusCode());
                }
                if (response.statusCode() != 200) {
                    // permanent (e.g. bad request / unknown model) -- do not retry
                    throw new RuntimeException("Ollama HTTP " + response.statusCode() + ": " + response.body());
                }

                JsonNode root = JSON.readTree(response.body());
                return root.path("response").asText("");
            } catch (IOException e) {
                throw new Retry.TransientFailure("network error calling Ollama: " + e, e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        });
    }
}

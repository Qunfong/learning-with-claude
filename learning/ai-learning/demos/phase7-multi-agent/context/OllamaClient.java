import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Pre-Phase-4-style Ollama client: a bare HttpClient wrapper with NO retry
 * logic. This is the "existing code" the Phase 7 ticket targets.
 *
 * NOTE: this is a deliberate stand-in, not the real phase4-agents
 * OllamaClient (that one already has retry, via Retry.withBackoff wrapping
 * chat() -- see demos/phase4-agents/src/main/java/OllamaClient.java). This
 * file exists so CoderAgent has real, retry-less code to fix, with a
 * complete(...) method matching the ticket text ("OllamaClient.complete()")
 * literally. It is data for the demo (read by OrchestratorAgent, like
 * phase4-agents' workspace/Calculator.java is data for CodingAgentDemo) --
 * NOT compiled as part of this module (it lives outside src/main/java).
 */
public class OllamaClient {

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final String baseUrl;

    public OllamaClient(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    /**
     * Sends one prompt to Ollama's /api/generate and returns the raw response
     * body. No retry: any transient network failure or 5xx response
     * propagates straight to the caller as an exception.
     */
    public String complete(String model, String prompt) throws IOException, InterruptedException {
        String body = "{\"model\":\"" + model + "\",\"prompt\":\"" + escape(prompt) + "\",\"stream\":false}";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/generate"))
                .timeout(Duration.ofMinutes(2))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Ollama HTTP " + response.statusCode() + ": " + response.body());
        }
        return response.body();
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}

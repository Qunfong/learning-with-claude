import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Fase 1 — Local vs Hosted, achter ÉÉN interface.
 *
 * Kernidee: de app kent alleen ModelClient.complete(prompt). Of dat naar een
 * lokaal Ollama-model gaat of naar de hosted Claude API is een implementatie-
 * detail (strategy pattern). Wisselen = een andere impl injecteren, geen
 * refactor van je app-code.
 *
 * JSON via Jackson (ObjectMapper) — bodies bouwen én responses parsen type-veilig.
 *
 * Draai met:  mvn -q compile exec:java
 *
 * - Ollama-backend: vereist `ollama serve` + `ollama pull llama3` lokaal.
 * - Claude-backend: vereist env var ANTHROPIC_API_KEY. Zonder key wordt die
 *   backend overgeslagen (demo draait dan alleen lokaal).
 */
public class LocalVsHostedDemo {

    // één ObjectMapper hergebruiken (thread-safe, duur om te maken)
    static final ObjectMapper JSON = new ObjectMapper();

    static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    // ---- de interface waar je hele app tegenaan praat ----------------------
    interface ModelClient {
        Result complete(String prompt) throws Exception;
        String name();
    }

    // resultaat + meetgegevens (latency, tokens) zodat we kunnen vergelijken
    record Result(String text, long latencyMs, int promptTokens, int outputTokens) {}

    // ---- backend 1: lokaal via Ollama --------------------------------------
    static final class OllamaClient implements ModelClient {
        final String model;
        OllamaClient(String model) { this.model = model; }
        public String name() { return "Ollama (lokaal, " + model + ")"; }

        public Result complete(String prompt) throws Exception {
            // request body type-veilig opbouwen i.p.v. string-concat
            ObjectNode body = JSON.createObjectNode();
            body.put("model", model);
            body.put("prompt", prompt);
            body.put("stream", false); // één JSON-antwoord met eval-counts erin

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:11434/api/generate"))
                    .timeout(Duration.ofMinutes(2))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)))
                    .build();

            long t0 = System.nanoTime();
            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            long ms = (System.nanoTime() - t0) / 1_000_000;
            if (resp.statusCode() != 200)
                throw new RuntimeException("Ollama HTTP " + resp.statusCode() + ": " + resp.body());

            JsonNode root = JSON.readTree(resp.body());
            return new Result(
                    root.path("response").asText(""),
                    ms,
                    root.path("prompt_eval_count").asInt(-1),
                    root.path("eval_count").asInt(-1));
        }
    }

    // ---- backend 2: hosted via Claude Messages API -------------------------
    static final class ClaudeClient implements ModelClient {
        final String model, apiKey;
        ClaudeClient(String model, String apiKey) { this.model = model; this.apiKey = apiKey; }
        public String name() { return "Claude (hosted, " + model + ")"; }

        public Result complete(String prompt) throws Exception {
            ObjectNode body = JSON.createObjectNode();
            body.put("model", model);
            body.put("max_tokens", 256);
            body.putArray("messages")
                .addObject()
                .put("role", "user")
                .put("content", prompt);

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.anthropic.com/v1/messages"))
                    .timeout(Duration.ofMinutes(2))
                    .header("Content-Type", "application/json")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01")
                    .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)))
                    .build();

            long t0 = System.nanoTime();
            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            long ms = (System.nanoTime() - t0) / 1_000_000;
            if (resp.statusCode() != 200)
                throw new RuntimeException("Claude HTTP " + resp.statusCode() + ": " + resp.body());

            // response: { content:[{type:"text",text:"..."}], usage:{input_tokens,output_tokens} }
            JsonNode root = JSON.readTree(resp.body());
            String text = root.path("content").path(0).path("text").asText("");
            JsonNode usage = root.path("usage");
            return new Result(
                    text,
                    ms,
                    usage.path("input_tokens").asInt(-1),
                    usage.path("output_tokens").asInt(-1));
        }
    }

    // ---- main: dezelfde prompt door elke beschikbare backend ---------------
    public static void main(String[] args) {
        String prompt = "Leg in 2 zinnen uit wat een token is in een LLM.";

        List<ModelClient> clients = new ArrayList<>();
        clients.add(new OllamaClient("llama3.2:3b"));

        String key = System.getenv("ANTHROPIC_API_KEY");
        if (key != null && !key.isBlank()) {
            clients.add(new ClaudeClient("claude-sonnet-5", key));
        } else {
            System.out.println("[i] ANTHROPIC_API_KEY niet gezet -> Claude-backend overgeslagen.\n");
        }

        // let op: de loop kent alleen ModelClient — geen if/else per backend
        for (ModelClient c : clients) {
            System.out.println("=== " + c.name() + " ===");
            try {
                Result r = c.complete(prompt);
                System.out.println("antwoord : " + r.text().strip());
                System.out.printf("latency  : %d ms%n", r.latencyMs());
                System.out.printf("tokens   : in=%d  uit=%d%n%n", r.promptTokens(), r.outputTokens());
            } catch (Exception e) {
                System.out.println("FOUT: " + e.getMessage() + "\n");
            }
        }
    }
}

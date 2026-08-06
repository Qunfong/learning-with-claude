import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/**
 * Fase 1 — Twee lokale modellen, één interface.
 *
 * Kernidee: dezelfde ModelClient-interface dekt llama3.2:3b én gemma2:2b.
 * De app-loop weet niet welk model draait — alleen de constructors verschillen.
 *
 * Open-ended prompt → antwoorden zullen echt afwijken (woordkeuze, volgorde,
 * nadruk). Dat is het punt: zelfde interface, ander gedrag.
 *
 * Draai met:
 *   mvn -q compile exec:java -Dexec.mainClass=TwoModelsDemo
 *
 * Vereist: `ollama serve` + beide modellen gepulled.
 */
public class TwoModelsDemo {

    static final ObjectMapper JSON = new ObjectMapper();

    static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    // ---- contract: de enige API die de app-code ziet -------------------------
    interface ModelClient {
        Result complete(String prompt) throws Exception;
        String name();
    }

    record Result(String text, long latencyMs, int outputTokens) {}

    // ---- implementatie: één class dekt elk Ollama-model ----------------------
    static final class OllamaClient implements ModelClient {
        private final String model;

        OllamaClient(String model) { this.model = model; }

        @Override
        public String name() { return "Ollama/" + model; }

        @Override
        public Result complete(String prompt) throws Exception {
            ObjectNode body = JSON.createObjectNode();
            body.put("model", model);
            body.put("prompt", prompt);
            body.put("stream", false);

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:11434/api/generate"))
                    .timeout(Duration.ofMinutes(3))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)))
                    .build();

            long t0 = System.nanoTime();
            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            long ms = (System.nanoTime() - t0) / 1_000_000;

            if (resp.statusCode() != 200)
                throw new RuntimeException("HTTP " + resp.statusCode() + ": " + resp.body());

            JsonNode root = JSON.readTree(resp.body());
            return new Result(
                    root.path("response").asText("").strip(),
                    ms,
                    root.path("eval_count").asInt(-1));
        }
    }

    // ---- app-code: weet niets van model-namen --------------------------------
    public static void main(String[] args) {
        String prompt = "Noem 3 concrete eigenschappen van goede code. Wees kort en direct.";

        List<ModelClient> clients = List.of(
                new OllamaClient("llama3.2:3b"),
                new OllamaClient("gemma2:2b")
        );

        System.out.println("Prompt: " + prompt);
        System.out.println("=".repeat(60));

        for (ModelClient client : clients) {
            System.out.println("\n>>> " + client.name());
            try {
                Result r = client.complete(prompt);
                System.out.println(r.text());
                System.out.printf("[latency: %d ms | tokens uit: %d]%n", r.latencyMs(), r.outputTokens());
            } catch (Exception e) {
                System.out.println("FOUT: " + e.getMessage());
            }
            System.out.println("-".repeat(60));
        }

        System.out.println("\nObserveer: zelfde interface-aanroep, verschillende antwoorden.");
        System.out.println("De loop op regel 80 is identiek voor elk model — dat is het punt.");
    }
}
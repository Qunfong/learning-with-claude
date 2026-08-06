import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Fase 0 — Tokens & sampling in de praktijk.
 *
 * Roept een echt lokaal model aan via Ollama en toont je WERKELIJKE
 * tokengebruik:
 *   - prompt_eval_count = input-tokens (je prompt na tokenization)
 *   - eval_count        = output-tokens (het gegenereerde antwoord)
 *
 * Draait dezelfde prompt op twee temperatures zodat je ziet hoe sampling
 * het gedrag stuurt: laag (0.0) = deterministisch/saai, hoog (1.0) = gevarieerd.
 *
 * Vereist Ollama lokaal:  ollama pull llama3.2:3b && ollama serve
 * Draai met:  mvn -q compile exec:java -Dexec.mainClass=OllamaDemo
 */
public class OllamaDemo {

    static final ObjectMapper JSON = new ObjectMapper();
    static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    record Result(String text, int promptTokens, int outputTokens) {}

    static Result generate(String model, String prompt, double temperature) throws Exception {
        ObjectNode body = JSON.createObjectNode();
        body.put("model", model);
        body.put("prompt", prompt);
        body.put("stream", false);
        body.putObject("options").put("temperature", temperature); // sampling-knop

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:11434/api/generate"))
                .timeout(Duration.ofMinutes(2))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)))
                .build();

        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200)
            throw new RuntimeException("Ollama HTTP " + resp.statusCode() + ": " + resp.body());

        JsonNode root = JSON.readTree(resp.body());
        return new Result(
                root.path("response").asText(""),
                root.path("prompt_eval_count").asInt(-1),
                root.path("eval_count").asInt(-1));
    }

    public static void main(String[] args) {
        String model = "llama3.2:3b";
        String prompt = "Noem in één zin een verrassend feit over de oceaan.";

        for (double temp : new double[]{0.0, 1.0}) {
            System.out.printf("=== temperature %.1f ===%n", temp);
            try {
                Result r = generate(model, prompt, temp);
                System.out.println("antwoord : " + r.text().strip());
                System.out.printf("tokens   : in=%d  uit=%d%n%n", r.promptTokens(), r.outputTokens());
            } catch (Exception e) {
                System.out.println("FOUT: " + e.getMessage());
                System.out.println("(draait Ollama? `ollama serve` en `ollama pull " + model + "`)\n");
                return; // tweede call heeft geen zin als de eerste faalt
            }
        }
        System.out.println("Zelfde prompt, ander sampling-gedrag. Input-tokens gelijk; " +
                "output kan verschillen in lengte en inhoud.");
    }
}

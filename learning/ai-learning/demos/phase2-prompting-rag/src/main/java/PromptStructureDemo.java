import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Fase 2 — Demo 1: Prompt-structuur maakt verschil.
 *
 * Zelfde vraag, 3x verstuurd met andere prompt-opbouw:
 *   A) Bare prompt  — geen system prompt, geen voorbeelden
 *   B) System prompt — rol + format instructie
 *   C) Few-shot     — 2 voorbeelden in context vóór de vraag
 *
 * Observeer: antwoordstijl, lengte en precisie veranderen significant
 * zonder dat het model of de vraag verandert. Alleen de prompt-structuur.
 *
 * Draai: mvn -q compile exec:java -Dexec.mainClass=PromptStructureDemo
 */
public class PromptStructureDemo {

    static final ObjectMapper JSON = new ObjectMapper();
    static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    static final String MODEL = "llama3.2:3b";

    // Ollama /api/chat — ondersteunt system/user/assistant rollen
    static String chat(String systemPrompt, String... turns) throws Exception {
        ObjectNode body = JSON.createObjectNode();
        body.put("model", MODEL);
        body.put("stream", false);

        ArrayNode messages = body.putArray("messages");
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.addObject().put("role", "system").put("content", systemPrompt);
        }
        // turns = afwisselend user/assistant/user/assistant...
        String[] roles = {"user", "assistant"};
        for (int i = 0; i < turns.length; i++) {
            messages.addObject().put("role", roles[i % 2]).put("content", turns[i]);
        }

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:11434/api/chat"))
                .timeout(Duration.ofMinutes(2))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)))
                .build();

        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200)
            throw new RuntimeException("Ollama HTTP " + resp.statusCode() + ": " + resp.body());

        JsonNode root = JSON.readTree(resp.body());
        return root.path("message").path("content").asText("").strip();
    }

    static void run(String label, String system, String... turns) throws Exception {
        System.out.println("\n" + "=".repeat(60));
        System.out.println(">>> " + label);
        System.out.println("=".repeat(60));
        long t0 = System.currentTimeMillis();
        String answer = chat(system, turns);
        System.out.println(answer);
        System.out.printf("[%d ms]%n", System.currentTimeMillis() - t0);
    }

    public static void main(String[] args) throws Exception {
        String question = "Wat is een token in een LLM?";

        System.out.println("Vraag: \"" + question + "\"");
        System.out.println("Model: " + MODEL + " — identiek voor alle 3 runs");

        // A: geen system prompt, geen voorbeelden
        run("A) Bare prompt — geen sturing",
                null,
                question);

        // B: system prompt met rol + format-instructie
        run("B) System prompt — rol + format",
                """
                Je bent een Java-expert die developers opleidt.
                Beantwoord in precies 2 zinnen.
                Gebruik een Java-analogie om het concept te verduidelijken.
                Geen intro, geen afsluiting — alleen de 2 zinnen.""",
                question);

        // C: few-shot — 2 voorbeelden laten zien welk format verwacht wordt,
        //    dan de echte vraag. Model leert het patroon uit de voorbeelden.
        run("C) Few-shot — 2 voorbeelden als patroon",
                null,
                // voorbeeld 1
                "Wat is een embedding?",
                "Embedding: vector van floats die betekenis vastlegt. " +
                "Java-analogie: een `double[]` waarbij gerelateerde woorden " +
                "dicht bij elkaar liggen in de vectorruimte.",
                // voorbeeld 2
                "Wat is temperatuur in een LLM?",
                "Temperatuur: schaalt hoe vlak/scherp de kans-verdeling over tokens is. " +
                "Java-analogie: `Random` met een seed — laag = deterministisch, " +
                "hoog = meer willekeur.",
                // echte vraag
                question);

        System.out.println("\n" + "=".repeat(60));
        System.out.println("Les: zelfde model, zelfde vraag — andere prompt-structuur");
        System.out.println("geeft andere stijl, lengte en precisie.");
        System.out.println("=".repeat(60));
    }
}

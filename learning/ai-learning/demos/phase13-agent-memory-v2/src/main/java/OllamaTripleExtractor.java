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
 * The "one LLM call" triple extractor the task spec asks for: sends ALL
 * facts in a single {@code /api/chat} request and asks the model to return
 * subject/relation/object triples as JSON. Same HttpClient+Jackson pattern
 * as phase2's {@code RagDemo.complete()} / phase4's {@code OllamaClient}.
 *
 * Only used by {@link GraphMemoryDemo}'s live run (requires
 * {@code ollama serve} + a pulled model) — never by tests, so
 * {@code mvn test} stays offline and deterministic. See
 * {@link RuleBasedTripleExtractor} for the extractor tests actually exercise.
 */
final class OllamaTripleExtractor implements TripleExtractor {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String MODEL = "llama3.2:3b";

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Override
    public List<GraphMemory.Triple> extract(List<String> facts) {
        String factList = String.join("\n", facts.stream().map(f -> "- " + f).toList());
        String prompt = "Extract subject/relation/object triples from these facts. "
                + "Use relation USES, DEPENDS_ON or REQUIRES only. "
                + "Reply with ONLY a JSON array like "
                + "[{\"subject\":\"Project\",\"relation\":\"USES\",\"object\":\"Postgres\"}], no prose.\n\n"
                + factList;

        ObjectNode body = JSON.createObjectNode();
        body.put("model", MODEL);
        body.put("stream", false);
        body.putArray("messages").addObject().put("role", "user").put("content", prompt);

        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:11434/api/chat"))
                    .timeout(Duration.ofMinutes(2))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)))
                    .build();

            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                throw new RuntimeException("Ollama HTTP " + resp.statusCode() + ": " + resp.body());
            }

            String content = JSON.readTree(resp.body()).path("message").path("content").asText("");
            String jsonArray = content.substring(content.indexOf('['), content.lastIndexOf(']') + 1);
            JsonNode arr = JSON.readTree(jsonArray);

            List<GraphMemory.Triple> triples = new ArrayList<>();
            for (JsonNode n : arr) {
                triples.add(new GraphMemory.Triple(
                        n.path("subject").asText(""),
                        n.path("relation").asText(""),
                        n.path("object").asText("")));
            }
            return triples;
        } catch (Exception e) {
            throw new RuntimeException("triple extraction via Ollama failed: " + e.getMessage(), e);
        }
    }
}

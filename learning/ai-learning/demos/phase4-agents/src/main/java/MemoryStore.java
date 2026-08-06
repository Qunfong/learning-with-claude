import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Long-term memory: een plat JSON-bestand, geschreven/gelezen via expliciete
 * remember/recall tool-aanroepen — geen vector-DB, geen embeddings (dat is
 * fase2-terrein). Het punt is het MECHANISME (persistentie is een tool-call,
 * geen magie), niet de opslag-engine.
 *
 * Elke demo gebruikt zijn eigen namespace zodat geheugen niet lekt tussen
 * demo's die er los van elkaar over nadenken.
 */
class MemoryStore {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final Path file;

    MemoryStore(String namespace) {
        this.file = Path.of("memory-store", namespace + ".json");
    }

    boolean hasAny() {
        return !load().isEmpty();
    }

    synchronized void remember(String fact) {
        try {
            Files.createDirectories(file.getParent());
            ArrayNode facts = load();
            facts.add(fact);
            Files.writeString(file, facts.toPrettyString());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    synchronized String recall(String query) {
        ArrayNode facts = load();
        if (facts.isEmpty()) {
            return "(geen herinneringen opgeslagen)";
        }
        // simpelste mogelijke "retrieval": geeft gewoon alles terug -- geen
        // ranking/embeddings, het punt is persistentie, niet zoekkwaliteit
        StringBuilder sb = new StringBuilder();
        facts.forEach(f -> sb.append("- ").append(f.asText()).append("\n"));
        return sb.toString().strip();
    }

    private ArrayNode load() {
        try {
            if (!Files.exists(file)) {
                return JSON.createArrayNode();
            }
            return (ArrayNode) JSON.readTree(Files.readString(file));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    List<Tool> asTools() {
        Tool remember = new Tool("remember",
                "Sla een feit blijvend op, herbruikbaar in latere (losse) runs.",
                Tool.oneStringParam("fact", "Het feit om te onthouden."),
                false,
                a -> {
                    String fact = a.path("fact").asText();
                    if (fact.isBlank()) {
                        throw new IllegalArgumentException("verplicht argument 'fact' ontbreekt");
                    }
                    remember(fact);
                    return "onthouden: " + fact;
                });

        Tool recall = new Tool("recall",
                "Haal eerder opgeslagen feiten op.",
                Tool.oneStringParam("query", "Waar je naar zoekt."),
                false,
                a -> recall(a.path("query").asText()));

        return List.of(remember, recall);
    }
}

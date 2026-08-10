import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Verbatim copy-in of phase4-agents/src/main/java/MemoryStore.java's original
 * remember/recall/load logic (decoupled only from {@code Tool}, which is
 * phase4-private and out of scope here per the "no cross-module import" rule
 * — everything else, including the recall() body, is unchanged).
 *
 * This class exists ONLY to prove the "before" half of the before/after test
 * in {@code MemoryStoreV2Test}: phase4's own README says recall() "geeft
 * gewoon alles terug -- geen ranking/embeddings" and calls that an
 * "onbegrensde long-term memory... reëel risico." {@link MemoryStoreV2} is
 * the fix; this class is the fossil record of the bug it fixes.
 *
 * Do not add ranking/bounding here — that would defeat the point of the test.
 */
final class LegacyMemoryStore {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final Path file;

    LegacyMemoryStore(Path file) {
        this.file = file;
    }

    synchronized void remember(String fact) {
        try {
            if (file.getParent() != null) Files.createDirectories(file.getParent());
            ArrayNode facts = load();
            facts.add(fact);
            Files.writeString(file, facts.toPrettyString());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Original behaviour, unchanged: "simpelste mogelijke retrieval: geeft
     * gewoon alles terug -- geen ranking/embeddings, het punt is
     * persistentie, niet zoekkwaliteit." No topK, no bound, `query` is
     * accepted but never used.
     */
    synchronized String recall(String query) {
        ArrayNode facts = load();
        if (facts.isEmpty()) {
            return "(geen herinneringen opgeslagen)";
        }
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
}

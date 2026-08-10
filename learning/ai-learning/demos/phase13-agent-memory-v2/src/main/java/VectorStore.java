import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Copied in and adapted from phase2-prompting-rag's {@code RagDemo.VectorStore}
 * (brute-force {@code List<EmbeddedChunk>} + full linear cosine scan). Kept
 * here byte-for-byte in spirit, only renamed/decoupled from Ollama so it works
 * with any {@code float[]} vector (see {@link Embeddings}) — this is the
 * BASELINE that {@link AnnIndex} is benchmarked against, per the phase2 README's
 * own scope note: "VectorStore is hier een List — productie gebruikt
 * pgvector/Qdrant/Weaviate voor scale + persistentie."
 */
final class VectorStore {

    record Chunk(String text, String source) {
    }

    record EmbeddedChunk(Chunk chunk, float[] vector) {
    }

    record Hit(Chunk chunk, double score) {
    }

    private final List<EmbeddedChunk> store = new ArrayList<>();

    void index(Chunk chunk, float[] vector) {
        store.add(new EmbeddedChunk(chunk, vector));
    }

    /** Brute-force: score every single stored vector against the query. O(N). */
    List<Hit> query(float[] queryVec, int topK) {
        return store.stream()
                .map(ec -> new Hit(ec.chunk(), Embeddings.cosine(ec.vector(), queryVec)))
                .sorted(Comparator.comparingDouble(Hit::score).reversed())
                .limit(topK)
                .toList();
    }

    int size() {
        return store.size();
    }

    List<EmbeddedChunk> all() {
        return store;
    }
}

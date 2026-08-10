/**
 * Minimal re-implementation of {@code phase13-agent-memory-v2/Embeddings} -
 * copied in and trimmed, not imported.
 *
 * <p>A deterministic hashed bag-of-words vector. This is <b>not</b> a trained
 * embedding model and has zero semantic understanding beyond surface lexical
 * overlap - it exists so this module runs offline and byte-identically on every
 * machine. Phase 13's version adds character trigrams for sub-word robustness;
 * dropped here because the capstone's memory corpus is a handful of sentences
 * that share whole words.
 */
public final class Embeddings {

    public static final int DIMS = 64;

    private Embeddings() {
    }

    public static float[] embed(String text) {
        float[] vec = new float[DIMS];
        for (String token : text.toLowerCase().split("[^a-z0-9]+")) {
            if (token.isEmpty()) {
                continue;
            }
            int bucket = Math.floorMod(token.hashCode(), DIMS);
            vec[bucket] += 1.0f;
        }
        return normalize(vec);
    }

    private static float[] normalize(float[] vec) {
        double norm = 0;
        for (float v : vec) {
            norm += v * v;
        }
        norm = Math.sqrt(norm);
        if (norm == 0) {
            return vec;
        }
        for (int i = 0; i < vec.length; i++) {
            vec[i] /= (float) norm;
        }
        return vec;
    }

    public static double cosine(float[] a, float[] b) {
        double dot = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
        }
        return dot;
    }
}

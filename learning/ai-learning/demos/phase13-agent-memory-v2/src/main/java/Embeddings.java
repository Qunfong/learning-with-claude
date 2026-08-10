/**
 * Hand-rolled, fully offline "embedding" function.
 *
 * phase2's {@code RagDemo} calls Ollama's {@code /api/embed} live for every
 * chunk (see phase2-prompting-rag/src/main/java/RagDemo.java, {@code embed()}).
 * That's the right call for a phase about context-quality with a real model
 * in the loop. This module's job is different: benchmark an ANN index and a
 * ranking pipeline deterministically and offline (mvn test must not depend on
 * an Ollama server being up), while staying true to the "hand-roll it to
 * understand the mechanics" rule this repo applies everywhere else. So instead
 * of a network call, this is a deterministic hashed bag-of-words +
 * character-trigram embedding: words get a stronger weight (captures whole
 * tokens like "connectTimeout"), trigrams add partial/sub-word signal, and the
 * result is L2-normalized so {@link #cosine} behaves the same way it does in
 * phase2.
 *
 * This is NOT a claim that hashed n-grams are as good as a trained embedding
 * model — it is a stand-in that's good enough to rank text by lexical/semantic
 * overlap for this module's benchmarks, with zero external dependencies.
 */
final class Embeddings {

    private Embeddings() {
    }

    static final int DEFAULT_DIMS = 64;

    static float[] embed(String text) {
        return embed(text, DEFAULT_DIMS);
    }

    static float[] embed(String text, int dims) {
        float[] vec = new float[dims];
        String norm = text.toLowerCase();

        for (String word : norm.split("\\W+")) {
            if (word.isBlank()) continue;
            int idx = Math.floorMod(word.hashCode(), dims);
            vec[idx] += 2.0f;
        }

        for (int i = 0; i <= norm.length() - 3; i++) {
            String trigram = norm.substring(i, i + 3);
            if (trigram.isBlank()) continue;
            int idx = Math.floorMod(trigram.hashCode(), dims);
            vec[idx] += 1.0f;
        }

        normalize(vec);
        return vec;
    }

    static void normalize(float[] v) {
        double norm = 0;
        for (float x : v) norm += (double) x * x;
        norm = Math.sqrt(norm);
        if (norm == 0) return;
        for (int i = 0; i < v.length; i++) v[i] = (float) (v[i] / norm);
    }

    static double cosine(float[] a, float[] b) {
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            na += (double) a[i] * a[i];
            nb += (double) b[i] * b[i];
        }
        return (na == 0 || nb == 0) ? 0 : dot / (Math.sqrt(na) * Math.sqrt(nb));
    }
}

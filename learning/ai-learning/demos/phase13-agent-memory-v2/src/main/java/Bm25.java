import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * Hand-rolled BM25 (Robertson/Sparck-Jones) keyword scorer over a fixed
 * corpus of documents. This is the "keyword" half of the hybrid search in
 * {@link HybridSearch}, fused against {@link Embeddings}'s vector cosine
 * score via reciprocal rank fusion — the point being that BM25 catches exact
 * identifier matches (e.g. "connectTimeout") that a coarse hashed embedding
 * can dilute, while the vector side catches paraphrases/related wording that
 * pure keyword matching misses.
 */
final class Bm25 {

    private static final double K1 = 1.5;
    private static final double B = 0.75;

    private final List<List<String>> docs;
    private final double avgDocLen;
    private final Map<String, Integer> docFreq = new HashMap<>();

    Bm25(List<String> corpus) {
        this.docs = corpus.stream().map(Bm25::tokenize).toList();
        this.avgDocLen = docs.stream().mapToInt(List::size).average().orElse(0);
        for (List<String> doc : docs) {
            for (String term : new HashSet<>(doc)) docFreq.merge(term, 1, Integer::sum);
        }
    }

    static List<String> tokenize(String text) {
        return Arrays.stream(text.toLowerCase().split("\\W+"))
                .filter(s -> !s.isBlank())
                .toList();
    }

    double score(int docIndex, List<String> queryTerms) {
        List<String> doc = docs.get(docIndex);
        if (doc.isEmpty()) return 0.0;
        Map<String, Long> tf = new HashMap<>();
        for (String w : doc) tf.merge(w, 1L, Long::sum);

        int n = docs.size();
        double score = 0;
        for (String term : queryTerms) {
            long f = tf.getOrDefault(term, 0L);
            if (f == 0) continue;
            int docsWithTerm = docFreq.getOrDefault(term, 0);
            double idf = Math.log(1 + (n - docsWithTerm + 0.5) / (docsWithTerm + 0.5));
            double denom = f + K1 * (1 - B + B * doc.size() / avgDocLen);
            score += idf * (f * (K1 + 1)) / denom;
        }
        return score;
    }

    /** Ranks all documents by BM25 score against the query, descending. Ties keep doc order. */
    List<Integer> rank(String query) {
        List<String> terms = tokenize(query);
        List<Integer> ids = new ArrayList<>();
        for (int i = 0; i < docs.size(); i++) ids.add(i);
        ids.sort(Comparator.comparingDouble((Integer i) -> score(i, terms)).reversed());
        return ids;
    }
}

import java.util.HashSet;
import java.util.Set;

/**
 * From-scratch "cross-encoder-style" rerank score. A real cross-encoder
 * jointly encodes (query, document) as ONE input through a transformer and
 * outputs a single relevance score — much more accurate than comparing two
 * independently-computed embeddings, but far too expensive to run against an
 * entire corpus. This is a hand-rolled stand-in with the same shape: it looks
 * at the (query, document) PAIR directly rather than two separate vectors,
 * and it is deliberately pricier than {@link Embeddings#cosine} per call —
 * {@link HybridSearch} only ever invokes it on the small fused shortlist, not
 * the whole corpus.
 *
 * Combines three signals a bag-of-hashed-ngrams cosine cannot see on its own:
 *   - token Jaccard overlap (query terms vs. document terms)
 *   - an exact-phrase/identifier bonus (e.g. "connecttimeout" appearing
 *     verbatim is a much stronger signal than scattered term overlap)
 *   - a second, higher-resolution embedding cosine (double the dimensions of
 *     the first-pass embedding), modeling "spend more compute on the
 *     survivors."
 */
final class CrossEncoderRerank {

    private CrossEncoderRerank() {
    }

    static double score(String query, String document) {
        Set<String> queryTerms = new HashSet<>(Bm25.tokenize(query));
        Set<String> docTerms = new HashSet<>(Bm25.tokenize(document));
        if (queryTerms.isEmpty() || docTerms.isEmpty()) return 0.0;

        Set<String> intersection = new HashSet<>(queryTerms);
        intersection.retainAll(docTerms);
        Set<String> union = new HashSet<>(queryTerms);
        union.addAll(docTerms);
        double jaccard = (double) intersection.size() / union.size();

        String docLower = document.toLowerCase();
        long exactHits = queryTerms.stream()
                .filter(t -> t.length() > 3 && docLower.contains(t))
                .count();
        double exactBonus = queryTerms.isEmpty() ? 0 : (double) exactHits / queryTerms.size();

        double richCosine = Embeddings.cosine(
                Embeddings.embed(query, 128),
                Embeddings.embed(document, 128));

        // weighted blend: exact identifier matches matter most for this kind of
        // (code-comment) corpus, then lexical overlap, then the richer embedding
        return 0.5 * exactBonus + 0.3 * jaccard + 0.2 * richCosine;
    }
}

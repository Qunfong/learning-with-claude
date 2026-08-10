import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Hybrid search: BM25 (keyword) + vector cosine, combined via Reciprocal
 * Rank Fusion (Cormack, Clarke &amp; Buettcher, 2009), then a cross-encoder
 * -style rerank pass over ONLY the fused top-K candidates.
 *
 * Two-stage retrieve-then-rerank is the real-world pattern this models: the
 * first stage (BM25 + cheap vector cosine) has to be fast because it scores
 * the WHOLE corpus, so it uses cheap approximations; the second stage
 * ({@link CrossEncoderRerank}) can afford to be more expensive per item
 * because it only ever looks at the small shortlist the first stage already
 * narrowed down — that division of labor is the entire reason production RAG
 * systems bother with a separate rerank step instead of just using a fancier
 * embedding model everywhere.
 */
final class HybridSearch {

    private static final int RRF_K = 60;

    record Candidate(int docId, String text, double fusedScore, double rerankScore) {
    }

    private final List<String> corpus;
    private final Bm25 bm25;
    private final List<float[]> vectors;

    HybridSearch(List<String> corpus) {
        this.corpus = corpus;
        this.bm25 = new Bm25(corpus);
        this.vectors = corpus.stream().map(Embeddings::embed).toList();
    }

    /** Vector-only ranking (phase2's original approach): plain cosine similarity, no fusion, no rerank. */
    List<Integer> vectorOnlyRank(String query) {
        float[] qVec = Embeddings.embed(query);
        List<Integer> ids = new ArrayList<>();
        for (int i = 0; i < corpus.size(); i++) ids.add(i);
        ids.sort(Comparator.comparingDouble((Integer i) -> Embeddings.cosine(vectors.get(i), qVec)).reversed());
        return ids;
    }

    static List<Integer> reciprocalRankFusion(List<Integer> rankingA, List<Integer> rankingB, int topN) {
        Map<Integer, Double> fused = new HashMap<>();
        for (int rank = 0; rank < rankingA.size(); rank++) {
            fused.merge(rankingA.get(rank), 1.0 / (RRF_K + rank + 1), Double::sum);
        }
        for (int rank = 0; rank < rankingB.size(); rank++) {
            fused.merge(rankingB.get(rank), 1.0 / (RRF_K + rank + 1), Double::sum);
        }
        List<Integer> ids = new ArrayList<>(fused.keySet());
        ids.sort(Comparator.comparingDouble((Integer id) -> fused.get(id)).reversed());
        return ids.subList(0, Math.min(topN, ids.size()));
    }

    /** Full pipeline: BM25 rank + vector rank -> RRF fusion -> cross-encoder-style rerank of the fused top-K. */
    List<Candidate> hybridSearchWithRerank(String query, int fusionTopN, int finalTopK) {
        List<Integer> bm25Ranking = bm25.rank(query);
        List<Integer> vectorRanking = vectorOnlyRank(query);
        List<Integer> fusedIds = reciprocalRankFusion(bm25Ranking, vectorRanking, fusionTopN);

        List<Candidate> reranked = new ArrayList<>();
        for (int id : fusedIds) {
            double rerankScore = CrossEncoderRerank.score(query, corpus.get(id));
            reranked.add(new Candidate(id, corpus.get(id), 0, rerankScore));
        }
        reranked.sort(Comparator.comparingDouble(Candidate::rerankScore).reversed());
        return reranked.subList(0, Math.min(finalTopK, reranked.size()));
    }
}

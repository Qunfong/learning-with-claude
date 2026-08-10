import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Benchmark: hand-rolled {@link AnnIndex} (HNSW-lite) vs. {@link VectorStore}'s
 * brute-force linear scan (copied in from phase2's {@code RagDemo.VectorStore}),
 * on recall@k and latency, as N grows: 50 / 500 / 2000 synthetic embedded
 * chunks. Prints REAL measured numbers — see README.md "What actually
 * happened" for the captured output of this exact run.
 *
 * Run: mvn -q compile exec:java "-Dexec.mainClass=AnnBenchmark"
 */
public class AnnBenchmark {

    static final int DIMS = 32;
    static final int K = 5;
    static final int NUM_QUERIES = 30;
    static final int M = 16;
    static final int EF_CONSTRUCTION = 100;
    static final int EF_SEARCH = 50;

    public static void main(String[] args) {
        System.out.println("=".repeat(78));
        System.out.println("ANN benchmark: hand-rolled HNSW-lite (AnnIndex) vs. brute-force (VectorStore)");
        System.out.println("dims=" + DIMS + "  k=" + K + "  queries/run=" + NUM_QUERIES
                + "  M=" + M + "  efConstruction=" + EF_CONSTRUCTION + "  efSearch=" + EF_SEARCH);
        System.out.println("=".repeat(78));
        System.out.printf(Locale.US, "%-8s %14s %14s %12s %10s %14s%n",
                "N", "brute ms/q", "ann ms/q", "speedup", "recall@k", "ann comparisons");

        for (int n : new int[]{50, 500, 2000}) {
            runBenchmark(n);
        }
    }

    static void runBenchmark(int n) {
        int clusters = Math.max(2, n / 25);
        List<float[]> data = SyntheticData.generate(n, DIMS, clusters, 42L);

        VectorStore bruteForce = new VectorStore();
        for (int i = 0; i < data.size(); i++) {
            bruteForce.index(new VectorStore.Chunk("synthetic-chunk-" + i, "synthetic"), data.get(i));
        }

        AnnIndex ann = new AnnIndex(M, EF_CONSTRUCTION, 7L);
        for (float[] vec : data) ann.insert(vec);

        // queries: reuse a deterministic subset of the indexed points themselves
        // (guarantees each query has a well-defined true nearest-neighbor set)
        List<float[]> queries = new ArrayList<>();
        int step = Math.max(1, n / NUM_QUERIES);
        for (int i = 0; i < n && queries.size() < NUM_QUERIES; i += step) {
            queries.add(data.get(i));
        }

        long bruteTotalNs = 0;
        long annTotalNs = 0;
        long comparisonsTotal = 0;
        int recallHits = 0;
        int recallTotal = 0;

        for (float[] q : queries) {
            long t0 = System.nanoTime();
            List<VectorStore.Hit> bruteHits = bruteForce.query(q, K);
            long t1 = System.nanoTime();
            bruteTotalNs += (t1 - t0);

            Set<String> groundTruth = new HashSet<>();
            for (VectorStore.Hit h : bruteHits) groundTruth.add(h.chunk().text());

            long t2 = System.nanoTime();
            AnnIndex.Result annResult = ann.search(q, K, EF_SEARCH);
            long t3 = System.nanoTime();
            annTotalNs += (t3 - t2);
            comparisonsTotal += annResult.distanceComparisons();

            for (int id : annResult.ids()) {
                String name = "synthetic-chunk-" + id;
                if (groundTruth.contains(name)) recallHits++;
            }
            recallTotal += K;
        }

        double bruteMsPerQuery = bruteTotalNs / 1_000_000.0 / queries.size();
        double annMsPerQuery = annTotalNs / 1_000_000.0 / queries.size();
        double recallAtK = (double) recallHits / recallTotal;
        double avgComparisons = (double) comparisonsTotal / queries.size();
        double speedup = annMsPerQuery == 0 ? Double.POSITIVE_INFINITY : bruteMsPerQuery / annMsPerQuery;

        System.out.printf(Locale.US, "%-8d %14.4f %14.4f %11.2fx %10.3f %14.1f%n",
                n, bruteMsPerQuery, annMsPerQuery, speedup, recallAtK, avgComparisons);
    }
}

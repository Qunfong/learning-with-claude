import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Hand-rolled, simplified HNSW-style layered graph ANN index (Malkov &amp;
 * Yashunin, "Efficient and robust approximate nearest neighbor search using
 * Hierarchical Navigable Small World graphs"). Not production HNSW (no
 * deletion, no disk persistence, no SIMD) — but it is a REAL layered graph:
 * each insert assigns a random top layer, greedily descends from the entry
 * point through the upper (sparse) layers to find a good starting node, then
 * does a bounded beam search ("ef") at each layer to pick neighbors. Search
 * does the same: descend the coarse layers, then beam-search only layer 0.
 *
 * The key property that makes this different from {@link VectorStore}'s
 * linear scan: a search only computes distances against nodes it actually
 * VISITS during graph traversal (bounded by ef and the graph's degree), never
 * against all N stored vectors. {@link #search} exposes that visited count so
 * {@link AnnBenchmark} can prove it, not just assert it.
 */
final class AnnIndex {

    record Result(List<Integer> ids, int distanceComparisons) {
    }

    private final int m;               // max neighbors per node on layers > 0
    private final int m0;              // max neighbors per node on layer 0
    private final int efConstruction;
    private final double levelMult;
    private final Random random;

    private final List<float[]> vectors = new ArrayList<>();
    // neighbors.get(nodeId).get(layer) = neighbor ids of that node at that layer
    private final List<List<List<Integer>>> neighbors = new ArrayList<>();
    private int entryPoint = -1;

    AnnIndex(int m, int efConstruction, long seed) {
        this.m = m;
        this.m0 = m * 2;
        this.efConstruction = efConstruction;
        this.levelMult = 1.0 / Math.log(m);
        this.random = new Random(seed);
    }

    int size() {
        return vectors.size();
    }

    private static double dist(float[] a, float[] b) {
        return 1.0 - Embeddings.cosine(a, b);
    }

    private int randomLevel() {
        return (int) Math.floor(-Math.log(random.nextDouble()) * levelMult);
    }

    void insert(float[] vec) {
        int id = vectors.size();
        vectors.add(vec);
        int level = randomLevel();
        List<List<Integer>> nodeLayers = new ArrayList<>();
        for (int l = 0; l <= level; l++) nodeLayers.add(new ArrayList<>());
        neighbors.add(nodeLayers);

        if (entryPoint == -1) {
            entryPoint = id;
            return;
        }

        int topLevel = neighbors.get(entryPoint).size() - 1;
        int ep = entryPoint;

        // phase 1: greedy single-path descent through the sparse upper layers
        for (int l = topLevel; l > level; l--) {
            ep = greedyDescend(vec, ep, l);
        }

        // phase 2: beam-search + connect at every layer this node lives on
        List<Integer> entryPoints = new ArrayList<>(List.of(ep));
        for (int l = Math.min(level, topLevel); l >= 0; l--) {
            List<Integer> candidates = beamSearch(vec, entryPoints, efConstruction, l, null);
            int maxConn = (l == 0) ? m0 : m;
            List<Integer> selected = candidates.subList(0, Math.min(maxConn, candidates.size()));
            for (int nb : selected) {
                connect(id, nb, l);
                connect(nb, id, l);
                prune(nb, l, maxConn);
            }
            if (!candidates.isEmpty()) entryPoints = candidates;
        }

        if (level > topLevel) {
            entryPoint = id;
        }
    }

    private void connect(int from, int to, int layer) {
        List<Integer> list = neighbors.get(from).get(layer);
        if (!list.contains(to)) list.add(to);
    }

    private void prune(int nodeId, int layer, int maxConn) {
        List<Integer> list = neighbors.get(nodeId).get(layer);
        if (list.size() <= maxConn) return;
        float[] v = vectors.get(nodeId);
        list.sort(Comparator.comparingDouble(nb -> dist(v, vectors.get(nb))));
        neighbors.get(nodeId).set(layer, new ArrayList<>(list.subList(0, maxConn)));
    }

    /** Single-step greedy walk: keep moving to a strictly closer neighbor until none exists. */
    private int greedyDescend(float[] query, int start, int layer) {
        int current = start;
        double currentDist = dist(query, vectors.get(current));
        boolean improved = true;
        while (improved) {
            improved = false;
            for (int nb : layerNeighbors(current, layer)) {
                double d = dist(query, vectors.get(nb));
                if (d < currentDist) {
                    currentDist = d;
                    current = nb;
                    improved = true;
                }
            }
        }
        return current;
    }

    private List<Integer> layerNeighbors(int nodeId, int layer) {
        List<List<Integer>> node = neighbors.get(nodeId);
        return layer < node.size() ? node.get(layer) : List.of();
    }

    /**
     * Bounded beam search at one layer: explores outward from entryPoints,
     * never touching more than roughly `ef` distinct nodes. If {@code counter}
     * is non-null it is incremented once per distance computation, so callers
     * can measure exactly how many comparisons a search actually did.
     */
    private List<Integer> beamSearch(float[] query, List<Integer> entryPoints, int ef, int layer,
                                      AtomicInteger counter) {
        Set<Integer> visited = new HashSet<>(entryPoints);
        PriorityQueue<double[]> candidateHeap = new PriorityQueue<>(Comparator.comparingDouble(a -> a[1]));
        PriorityQueue<double[]> resultHeap = new PriorityQueue<>(Comparator.comparingDouble((double[] a) -> a[1]).reversed());

        for (int ep : entryPoints) {
            double d = dist(query, vectors.get(ep));
            if (counter != null) counter.incrementAndGet();
            candidateHeap.add(new double[]{ep, d});
            resultHeap.add(new double[]{ep, d});
        }

        while (!candidateHeap.isEmpty()) {
            double[] c = candidateHeap.poll();
            double worst = resultHeap.peek()[1];
            if (c[1] > worst && resultHeap.size() >= ef) break;

            int cId = (int) c[0];
            for (int nb : layerNeighbors(cId, layer)) {
                if (visited.contains(nb)) continue;
                visited.add(nb);
                double d = dist(query, vectors.get(nb));
                if (counter != null) counter.incrementAndGet();
                if (resultHeap.size() < ef || d < resultHeap.peek()[1]) {
                    candidateHeap.add(new double[]{nb, d});
                    resultHeap.add(new double[]{nb, d});
                    if (resultHeap.size() > ef) resultHeap.poll();
                }
            }
        }

        List<double[]> sorted = new ArrayList<>(resultHeap);
        sorted.sort(Comparator.comparingDouble(a -> a[1]));
        List<Integer> ids = new ArrayList<>();
        for (double[] r : sorted) ids.add((int) r[0]);
        return ids;
    }

    /** Approximate top-k search. Returns ids plus how many distance comparisons it actually took. */
    Result search(float[] query, int k, int efSearch) {
        if (entryPoint == -1) return new Result(List.of(), 0);

        AtomicInteger counter = new AtomicInteger();
        int topLevel = neighbors.get(entryPoint).size() - 1;
        int ep = entryPoint;
        for (int l = topLevel; l > 0; l--) {
            // count the descent's comparisons too -- it's real work, not free
            ep = greedyDescendCounting(query, ep, l, counter);
        }

        List<Integer> candidates = beamSearch(query, new ArrayList<>(List.of(ep)), Math.max(efSearch, k), 0, counter);
        List<Integer> topK = candidates.subList(0, Math.min(k, candidates.size()));
        return new Result(topK, counter.get());
    }

    private int greedyDescendCounting(float[] query, int start, int layer, AtomicInteger counter) {
        int current = start;
        double currentDist = dist(query, vectors.get(current));
        counter.incrementAndGet();
        boolean improved = true;
        while (improved) {
            improved = false;
            for (int nb : layerNeighbors(current, layer)) {
                double d = dist(query, vectors.get(nb));
                counter.incrementAndGet();
                if (d < currentDist) {
                    currentDist = d;
                    current = nb;
                    improved = true;
                }
            }
        }
        return current;
    }
}

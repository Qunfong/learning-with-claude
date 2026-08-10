import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Synthetic embedded-chunk vectors for {@link AnnBenchmark} — clustered
 * random unit vectors (a handful of "topic centroids" + gaussian noise per
 * point), which is what real embedded text chunks look like geometrically
 * (near-duplicates cluster, unrelated chunks are far apart) without needing
 * any real text or a live embedding model.
 */
final class SyntheticData {

    private SyntheticData() {
    }

    static List<float[]> generate(int n, int dims, int clusters, long seed) {
        Random rnd = new Random(seed);
        List<float[]> centroids = new ArrayList<>();
        for (int c = 0; c < clusters; c++) {
            centroids.add(randomUnitVector(dims, rnd));
        }

        List<float[]> points = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            float[] centroid = centroids.get(i % clusters);
            float[] point = new float[dims];
            for (int d = 0; d < dims; d++) {
                point[d] = centroid[d] + (float) (rnd.nextGaussian() * 0.15);
            }
            Embeddings.normalize(point);
            points.add(point);
        }
        return points;
    }

    private static float[] randomUnitVector(int dims, Random rnd) {
        float[] v = new float[dims];
        for (int d = 0; d < dims; d++) v[d] = (float) rnd.nextGaussian();
        Embeddings.normalize(v);
        return v;
    }
}

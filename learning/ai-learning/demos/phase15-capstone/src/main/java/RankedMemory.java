import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Minimal re-implementation of {@code phase13-agent-memory-v2/MemoryStoreV2} -
 * copied in and trimmed, not imported.
 *
 * <p>Keeps the three properties that make it a memory rather than a leak:
 * <ol>
 *   <li><b>ranked</b> by cosine similarity against the query, not returned in
 *       insertion order;</li>
 *   <li><b>bounded</b> - {@link #recall} never returns more than
 *       {@link #MAX_RESULTS} facts no matter what is asked for;</li>
 *   <li><b>decayed</b> - each fact carries a logical last-touched clock, and a
 *       fact that has not been recalled in a long time is demoted
 *       exponentially, so a stale-but-lexically-similar fact cannot dominate
 *       forever. Being recalled resets the clock.</li>
 * </ol>
 *
 * <p>Trimmed vs. phase 13: no JSON persistence (in-memory only - the capstone
 * is a single-process demo and a file would just be state to clean up between
 * runs) and no {@code recallCount}.
 *
 * <p>Each fact carries a {@code source} tag so {@link CapstoneEval} can ask a
 * question the eval layer actually cares about: did the recall surface what
 * THIS run just learned, or only pre-existing facts?
 */
public final class RankedMemory {

    public static final int MAX_RESULTS = 3;
    public static final double DECAY_RATE = 0.03;

    public record Fact(String text, String source, float[] vector, long lastTouchedAt) {
    }

    public record ScoredFact(String text, String source, double similarity, double decay,
                             double rankScore) {
    }

    private final List<Fact> facts = new ArrayList<>();
    private long clock;

    public void remember(String text, String source) {
        clock++;
        facts.add(new Fact(text, source, Embeddings.embed(text), clock));
    }

    public int size() {
        return facts.size();
    }

    public List<ScoredFact> recall(String query, int topK) {
        clock++;
        int bound = Math.min(topK <= 0 ? MAX_RESULTS : topK, MAX_RESULTS);
        float[] queryVec = Embeddings.embed(query);

        List<ScoredFact> ranked = new ArrayList<>();
        for (Fact f : facts) {
            double similarity = Embeddings.cosine(f.vector(), queryVec);
            double decay = Math.exp(-DECAY_RATE * (clock - f.lastTouchedAt()));
            ranked.add(new ScoredFact(f.text(), f.source(), similarity, decay, similarity * decay));
        }
        ranked.sort(Comparator.comparingDouble(ScoredFact::rankScore).reversed());
        List<ScoredFact> top = new ArrayList<>(ranked.subList(0, Math.min(bound, ranked.size())));

        // recall reinforcement: being returned resets the decay clock
        for (ScoredFact sf : top) {
            for (int i = 0; i < facts.size(); i++) {
                Fact f = facts.get(i);
                if (f.text().equals(sf.text())) {
                    facts.set(i, new Fact(f.text(), f.source(), f.vector(), clock));
                    break;
                }
            }
        }
        return top;
    }
}

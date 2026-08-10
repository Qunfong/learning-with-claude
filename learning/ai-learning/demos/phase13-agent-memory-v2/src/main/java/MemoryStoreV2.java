import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The fix for phase4-agents/MemoryStore.recall(), whose own README calls out
 * "onbegrensde long-term memory is een reëel risico... In productie: expiry,
 * relevantie-ranking (embeddings/RAG), of periodieke review."
 *
 * Three changes vs. {@link LegacyMemoryStore}:
 *   1. RANKED  — cosine similarity against the query, same approach as
 *      phase2's VectorStore (see {@link Embeddings}), instead of "return
 *      everything in insertion order."
 *   2. BOUNDED — recall() never returns more than {@link #MAX_RESULTS}
 *      facts, regardless of how many are stored or what topK is requested.
 *   3. DECAYED — combines two of phase4's own suggested remedies
 *      (relevance-ranking + periodic review) into one ranking signal: each
 *      fact carries a logical "last touched" clock (bumped on insert and on
 *      every recall that returns it). Facts that are semantically relevant
 *      but haven't been touched in a long time (recalled once, then never
 *      again, while many other remember/recall calls happened) decay toward
 *      irrelevance exponentially, so a stale-but-lexically-similar fact
 *      can't dominate a ranking forever purely by accident of wording.
 *      Chose decay-in-ranking over hard eviction because eviction is
 *      irreversible (a fact that turns out to matter again is gone for
 *      good); decay just demotes it, and a fresh `remember` of the same
 *      fact resets its clock.
 */
final class MemoryStoreV2 {

    private static final ObjectMapper JSON = new ObjectMapper();
    static final int MAX_RESULTS = 10;
    static final double DECAY_RATE = 0.03;

    record Fact(String text, float[] vector, long insertedAt, long lastTouchedAt, int recallCount) {
    }

    record ScoredFact(String text, double similarity, double decay, double rankScore) {
    }

    private final Path file;
    private final List<Fact> facts = new ArrayList<>();
    private long clock;

    MemoryStoreV2(Path file) {
        this.file = file;
        load();
    }

    synchronized void remember(String text) {
        clock++;
        float[] vec = Embeddings.embed(text);
        facts.add(new Fact(text, vec, clock, clock, 0));
        persist();
    }

    int factCount() {
        return facts.size();
    }

    /**
     * Ranked top-K recall, hard-bounded at {@link #MAX_RESULTS}. Returned
     * facts have their "last touched" clock bumped (decay reset) — being
     * recalled is itself a signal the fact is still useful.
     */
    synchronized List<ScoredFact> recall(String query, int topK) {
        clock++;
        int bound = Math.min(topK <= 0 ? MAX_RESULTS : topK, MAX_RESULTS);
        float[] qVec = Embeddings.embed(query);

        List<ScoredFact> ranked = new ArrayList<>();
        for (Fact f : facts) {
            double similarity = Embeddings.cosine(f.vector(), qVec);
            double decay = Math.exp(-DECAY_RATE * (clock - f.lastTouchedAt()));
            ranked.add(new ScoredFact(f.text(), similarity, decay, similarity * decay));
        }
        ranked.sort(Comparator.comparingDouble(ScoredFact::rankScore).reversed());
        List<ScoredFact> top = ranked.subList(0, Math.min(bound, ranked.size()));

        // touching = recall reinforcement: reset decay clock on returned facts
        for (ScoredFact sf : top) {
            for (int i = 0; i < facts.size(); i++) {
                Fact f = facts.get(i);
                if (f.text().equals(sf.text())) {
                    facts.set(i, new Fact(f.text(), f.vector(), f.insertedAt(), clock, f.recallCount() + 1));
                    break;
                }
            }
        }
        persist();
        return new ArrayList<>(top);
    }

    private void persist() {
        try {
            if (file.getParent() != null) Files.createDirectories(file.getParent());
            ArrayNode arr = JSON.createArrayNode();
            for (Fact f : facts) {
                ObjectNode node = arr.addObject();
                node.put("text", f.text());
                node.put("insertedAt", f.insertedAt());
                node.put("lastTouchedAt", f.lastTouchedAt());
                node.put("recallCount", f.recallCount());
                ArrayNode vecNode = node.putArray("vector");
                for (float v : f.vector()) vecNode.add(v);
            }
            ObjectNode root = JSON.createObjectNode();
            root.put("clock", clock);
            root.set("facts", arr);
            Files.writeString(file, root.toPrettyString());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void load() {
        try {
            if (!Files.exists(file)) return;
            JsonNode root = JSON.readTree(Files.readString(file));
            clock = root.path("clock").asLong(0);
            for (JsonNode n : root.path("facts")) {
                JsonNode vecNode = n.path("vector");
                float[] vec = new float[vecNode.size()];
                for (int i = 0; i < vec.length; i++) vec[i] = (float) vecNode.get(i).asDouble();
                facts.add(new Fact(n.path("text").asText(), vec,
                        n.path("insertedAt").asLong(0), n.path("lastTouchedAt").asLong(0),
                        n.path("recallCount").asInt(0)));
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}

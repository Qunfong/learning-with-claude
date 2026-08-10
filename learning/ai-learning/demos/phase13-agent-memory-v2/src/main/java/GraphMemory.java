import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Small in-memory graph memory: entity/relation triples (e.g. {@code Project
 * USES Postgres}) held as an adjacency list, keyed by subject. This is the
 * "Graph RAG" gap named in the gap review: vector similarity is bad at
 * answering structural questions like "what depends on X" (a fact about
 * relationships, not about text that reads similarly), which is exactly what
 * a graph traversal answers directly and a cosine search can only guess at.
 *
 * Deliberately NOT a general graph DB: no query language, one relation
 * direction stored per triple, adjacency list only (no reverse index) —
 * {@link #whatDependsOn} does a full scan of stored triples, which is fine at
 * demo scale and keeps the "no external graph-DB dependency" constraint
 * honest (this is not pretending to be Neo4j).
 */
final class GraphMemory {

    record Triple(String subject, String relation, String object) {
        Triple {
            subject = subject.trim();
            relation = relation.trim().toUpperCase();
            object = object.trim();
        }
    }

    private final Map<String, List<Triple>> outgoingBySubject = new HashMap<>();
    private final List<Triple> all = new ArrayList<>();

    void addTriple(Triple t) {
        outgoingBySubject.computeIfAbsent(t.subject().toLowerCase(), k -> new ArrayList<>()).add(t);
        all.add(t);
    }

    List<Triple> relationsOf(String subject) {
        return outgoingBySubject.getOrDefault(subject.toLowerCase(), List.of());
    }

    /**
     * Impact analysis: "what depends on X" — reverse traversal over USES /
     * DEPENDS_ON / REQUIRES edges whose object matches the target. Returns an
     * EMPTY list, never a guess, when no such edge was ever stored — the
     * whole point of {@code GraphMemoryTest}'s negative test is proving this
     * doesn't hallucinate an edge that was never extracted.
     */
    List<String> whatDependsOn(String target) {
        List<String> result = new ArrayList<>();
        for (Triple t : all) {
            boolean dependencyRelation = t.relation().equals("USES")
                    || t.relation().equals("DEPENDS_ON")
                    || t.relation().equals("REQUIRES");
            if (dependencyRelation && t.object().equalsIgnoreCase(target)) {
                result.add(t.subject());
            }
        }
        return result;
    }

    int size() {
        return all.size();
    }

    List<Triple> all() {
        return List.copyOf(all);
    }
}

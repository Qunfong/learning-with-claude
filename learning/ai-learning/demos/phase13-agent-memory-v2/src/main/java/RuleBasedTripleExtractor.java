import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic, offline stand-in for the LLM-based extraction pass
 * ({@link OllamaTripleExtractor}). Matches simple "SUBJECT relation OBJECT"
 * sentence patterns — good enough for the controlled facts this demo/tests
 * feed it, not a real NLP relation-extraction system. Used so
 * {@code GraphMemoryTest} runs without a live model.
 */
final class RuleBasedTripleExtractor implements TripleExtractor {

    private static final List<Pattern> PATTERNS = List.of(
            Pattern.compile("^(?<subject>.+?)\\s+depends\\s+on\\s+(?<object>.+)$", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^(?<subject>.+?)\\s+requires\\s+(?<object>.+)$", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^(?<subject>.+?)\\s+uses\\s+(?<object>.+)$", Pattern.CASE_INSENSITIVE)
    );

    @Override
    public List<GraphMemory.Triple> extract(List<String> facts) {
        List<GraphMemory.Triple> triples = new ArrayList<>();
        for (String fact : facts) {
            String cleaned = fact.trim().replaceAll("[.!]+$", "");
            for (Pattern p : PATTERNS) {
                Matcher m = p.matcher(cleaned);
                if (m.matches()) {
                    String relation = relationFor(p);
                    triples.add(new GraphMemory.Triple(m.group("subject"), relation, m.group("object")));
                    break;
                }
            }
        }
        return triples;
    }

    private String relationFor(Pattern p) {
        String pattern = p.pattern();
        if (pattern.contains("depends")) return "DEPENDS_ON";
        if (pattern.contains("requires")) return "REQUIRES";
        return "USES";
    }
}

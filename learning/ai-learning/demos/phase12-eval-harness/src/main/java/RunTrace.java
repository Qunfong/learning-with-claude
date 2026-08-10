import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Parsed form of one {@code phase8-autonomous/traces/run-{id}.jsonl} file
 * (the {@code ObservabilityCollector} format: {@code step}, {@code
 * gate_decision}, {@code test_run}, {@code file_written}, {@code
 * run_summary} events, one JSON object per line, in emission order).
 *
 * Deliberately a thin wrapper around the raw {@link JsonNode} list rather
 * than a fully-typed event hierarchy -- this harness is a READ-ONLY
 * consumer of an already-committed, already-stable log format (phase8 owns
 * the schema), so re-parsing every field into strongly-typed records would
 * just be parallel maintenance for fields most evaluators never touch. Each
 * evaluator layer pulls the handful of fields it needs directly off the
 * node.
 */
public final class RunTrace {

    private final String runId;
    private final List<JsonNode> events;

    public RunTrace(String runId, List<JsonNode> events) {
        this.runId = runId;
        this.events = List.copyOf(events);
    }

    public String runId() {
        return runId;
    }

    public List<JsonNode> events() {
        return events;
    }

    public List<JsonNode> eventsOfType(String eventName) {
        List<JsonNode> out = new ArrayList<>();
        for (JsonNode e : events) {
            if (eventName.equals(text(e, "event"))) {
                out.add(e);
            }
        }
        return out;
    }

    /** Index (position in {@link #events()}) of the first event matching the predicate, or -1. */
    public int firstIndexWhere(java.util.function.Predicate<JsonNode> predicate) {
        for (int i = 0; i < events.size(); i++) {
            if (predicate.test(events.get(i))) {
                return i;
            }
        }
        return -1;
    }

    public JsonNode runSummary() {
        List<JsonNode> summaries = eventsOfType("run_summary");
        return summaries.isEmpty() ? null : summaries.get(summaries.size() - 1);
    }

    /** Raw {@code outcome} string from run_summary, e.g. "pr-simulated" or "escalated (3_consecutive_test_failures)". Null if absent. */
    public String recordedOutcome() {
        JsonNode summary = runSummary();
        return summary == null ? null : text(summary, "outcome");
    }

    public boolean recordedOutcomeIsSuccess() {
        String outcome = recordedOutcome();
        return outcome != null && outcome.startsWith("pr-simulated");
    }

    public boolean recordedOutcomeIsEscalate() {
        String outcome = recordedOutcome();
        return outcome != null && outcome.startsWith("escalated");
    }

    public List<JsonNode> testRuns() {
        return eventsOfType("test_run");
    }

    /** The captured pass/fail of the LAST test_run in the trace -- the "test exit code" ground truth per AWS's task-level layer. Null if no test ever ran. */
    public Boolean lastTestPassed() {
        List<JsonNode> runs = testRuns();
        if (runs.isEmpty()) {
            return null;
        }
        JsonNode last = runs.get(runs.size() - 1);
        return last.has("passed") && last.get("passed").asBoolean();
    }

    public boolean anyTestPassed() {
        for (JsonNode t : testRuns()) {
            if (t.has("passed") && t.get("passed").asBoolean()) {
                return true;
            }
        }
        return false;
    }

    public List<JsonNode> gateDecisions(String gate) {
        List<JsonNode> out = new ArrayList<>();
        for (JsonNode e : eventsOfType("gate_decision")) {
            if (gate.equals(text(e, "gate"))) {
                out.add(e);
            }
        }
        return out;
    }

    public List<JsonNode> codingSteps() {
        List<JsonNode> out = new ArrayList<>();
        for (JsonNode e : eventsOfType("step")) {
            if ("coding".equals(text(e, "step"))) {
                out.add(e);
            }
        }
        return out;
    }

    public List<JsonNode> planningSteps() {
        List<JsonNode> out = new ArrayList<>();
        for (JsonNode e : eventsOfType("step")) {
            if ("planning".equals(text(e, "step"))) {
                out.add(e);
            }
        }
        return out;
    }

    /** Sum of every step's tokensIn/tokensOut, as an independent cross-check against run_summary's own totals. */
    public long recomputedTotalTokens() {
        long total = 0;
        for (JsonNode e : eventsOfType("step")) {
            total += longField(e, "tokensIn") + longField(e, "tokensOut");
        }
        return total;
    }

    public int totalTokensIn() {
        JsonNode s = runSummary();
        return s == null ? 0 : (int) longField(s, "tokensIn");
    }

    public int totalTokensOut() {
        JsonNode s = runSummary();
        return s == null ? 0 : (int) longField(s, "tokensOut");
    }

    public double totalCostUsd() {
        JsonNode s = runSummary();
        return s == null || !s.has("costUsd") ? 0.0 : s.get("costUsd").asDouble();
    }

    public long durationMs() {
        JsonNode s = runSummary();
        return s == null ? 0 : longField(s, "durationMs");
    }

    /** Optional, non-standard field this harness's synthetic fixtures use to model "confidence markers" -- averaged across whatever step events carry it. Real phase8 traces don't have it, so this returns 1.0 (neutral) for them. */
    public double averageConfidenceOrNeutral() {
        double sum = 0;
        int count = 0;
        for (JsonNode e : eventsOfType("step")) {
            if (e.has("confidence")) {
                sum += e.get("confidence").asDouble();
                count++;
            }
        }
        return count == 0 ? 1.0 : sum / count;
    }

    private static long longField(JsonNode node, String field) {
        return node.has(field) ? node.get(field).asLong() : 0L;
    }

    private static String text(JsonNode node, String field) {
        return node.has(field) ? node.get(field).asText() : null;
    }
}

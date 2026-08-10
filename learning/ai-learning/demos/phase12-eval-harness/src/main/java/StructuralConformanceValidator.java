import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/**
 * AWS three-layer framework, layer 2a: trajectory quality -- structural
 * conformance. Parses each trace's state-transition order and checks the
 * invariants phase8-autonomous's own state machine README diagram
 * guarantees (PLANNING -> GATE1 -> CODING -> TESTING -> GATE2 -> PR, with a
 * RETRY x3 -> ESCALATE safety valve). No LLM, no judgment call -- either the
 * event order satisfies the invariant or it doesn't.
 */
public final class StructuralConformanceValidator {

    public record Violation(String rule, String detail) {
    }

    private StructuralConformanceValidator() {
    }

    public static List<Violation> validate(RunTrace trace) {
        List<Violation> violations = new ArrayList<>();

        checkGate1PrecedesCoding(trace, violations);
        checkTestingPrecedesGate2(trace, violations);
        checkEveryCodingFollowedByTesting(trace, violations);
        checkTerminalEvent(trace, violations);
        checkEscalateHasNoGate2(trace, violations);

        return violations;
    }

    /** Rule: GATE1 must precede CODING -- no coding step may occur before an approved gate1 decision. */
    private static void checkGate1PrecedesCoding(RunTrace trace, List<Violation> violations) {
        int firstCoding = trace.firstIndexWhere(e -> "step".equals(text(e, "event")) && "coding".equals(text(e, "step")));
        if (firstCoding == -1) {
            return; // no coding step at all -- nothing to violate
        }
        int firstApprovedGate1 = trace.firstIndexWhere(e ->
                "gate_decision".equals(text(e, "event")) && "gate1".equals(text(e, "gate")) && e.path("approved").asBoolean(false));
        if (firstApprovedGate1 == -1 || firstApprovedGate1 > firstCoding) {
            violations.add(new Violation("GATE1_BEFORE_CODING",
                    "first CODING step at index " + firstCoding + " has no preceding approved gate1 decision"));
        }
    }

    /** Rule: TESTING must precede GATE2 -- any gate2 decision must be preceded by a passing test_run. */
    private static void checkTestingPrecedesGate2(RunTrace trace, List<Violation> violations) {
        int firstGate2 = trace.firstIndexWhere(e -> "gate_decision".equals(text(e, "event")) && "gate2".equals(text(e, "gate")));
        if (firstGate2 == -1) {
            return; // no gate2 reached -- nothing to violate
        }
        int firstPassingTest = trace.firstIndexWhere(e -> "test_run".equals(text(e, "event")) && e.path("passed").asBoolean(false));
        if (firstPassingTest == -1 || firstPassingTest > firstGate2) {
            violations.add(new Violation("TESTING_BEFORE_GATE2",
                    "GATE2 decision at index " + firstGate2 + " has no preceding passing test_run"));
        }
    }

    /** Rule: every CODING step must be followed by a test_run before the run ends (no orphaned code write). */
    private static void checkEveryCodingFollowedByTesting(RunTrace trace, List<Violation> violations) {
        List<JsonNode> events = trace.events();
        for (int i = 0; i < events.size(); i++) {
            JsonNode e = events.get(i);
            if (!("step".equals(text(e, "event")) && "coding".equals(text(e, "step")))) {
                continue;
            }
            boolean followedByTest = false;
            for (int j = i + 1; j < events.size(); j++) {
                if ("test_run".equals(text(events.get(j), "event"))) {
                    followedByTest = true;
                    break;
                }
            }
            if (!followedByTest) {
                violations.add(new Violation("CODING_FOLLOWED_BY_TESTING",
                        "CODING step at index " + i + " has no subsequent test_run"));
            }
        }
    }

    /** Rule: the trace must end with a run_summary -- a trace with no terminal event describes an unfinished/truncated run. */
    private static void checkTerminalEvent(RunTrace trace, List<Violation> violations) {
        List<JsonNode> events = trace.events();
        if (events.isEmpty() || !"run_summary".equals(text(events.get(events.size() - 1), "event"))) {
            violations.add(new Violation("TERMINAL_RUN_SUMMARY", "last event is not run_summary (trace looks truncated)"));
        }
    }

    /** Rule: an escalated run must never have reached an approved gate2 -- ESCALATE and PR-open are mutually exclusive outcomes. */
    private static void checkEscalateHasNoGate2(RunTrace trace, List<Violation> violations) {
        if (!trace.recordedOutcomeIsEscalate()) {
            return;
        }
        boolean hasApprovedGate2 = trace.firstIndexWhere(e ->
                "gate_decision".equals(text(e, "event")) && "gate2".equals(text(e, "gate")) && e.path("approved").asBoolean(false)) != -1;
        if (hasApprovedGate2) {
            violations.add(new Violation("ESCALATE_EXCLUDES_GATE2",
                    "run_summary claims escalation but an approved gate2 decision is also present"));
        }
    }

    private static String text(JsonNode node, String field) {
        return node.has(field) ? node.get(field).asText() : null;
    }
}

import java.util.ArrayList;
import java.util.List;

/**
 * Minimal re-implementation of {@code phase12-eval-harness}'s
 * {@code StructuralConformanceValidator} + {@code EvalHarness.score}, folded
 * into one class - copied in, not imported.
 *
 * <p>Two operations, both deterministic and both LLM-free, because a CI gate
 * needs a number that does not change between two runs over the same trace:
 * <ul>
 *   <li>{@link #violations(RunTrace)} - layer 2, trajectory conformance:
 *       replays the event order against this pipeline's invariants. No
 *       judgement calls, just rules.</li>
 *   <li>{@link #score(RunTrace)} - the 0-100 composite a gate thresholds on.</li>
 * </ul>
 *
 * <p>Trimmed vs. phase 12: no LLM-as-judge layer (phase 12's own README is
 * blunt that its {@code OllamaJudge} is unexercised scaffolding anyway) and no
 * system-health/cost aggregation - there is no model call in this module to
 * spend tokens on.
 */
public final class CapstoneEval {

    /** The threshold the confidence gate uses, and the one this validator re-checks against. */
    public static final double CONFIDENCE_THRESHOLD = 0.5;

    /** What a CI gate would threshold the composite score on. */
    public static final double CI_GATE = 70.0;

    private static final double VIOLATION_PENALTY = 15.0;

    private CapstoneEval() {
    }

    /**
     * Structural invariants of the capstone chain. Each one is a thing that
     * could go wrong in a real implementation and that the trace - not the
     * agent's own summary - can prove either way.
     */
    public static List<String> violations(RunTrace trace) {
        List<String> found = new ArrayList<>();
        List<RunTrace.Event> events = trace.events();

        boolean credentialSeen = false;
        boolean abstained = false;
        for (RunTrace.Event e : events) {
            switch (e.kind()) {
                case "credential_minted" -> credentialSeen = true;
                case "tool_call" -> {
                    if (!credentialSeen) {
                        found.add("TOOL_CALL_BEFORE_CREDENTIAL: tool '" + e.text("tool")
                                + "' ran before any credential was minted");
                    }
                    if (!e.flag("authorized") && e.flag("executed")) {
                        found.add("UNAUTHORIZED_EXECUTION: tool '" + e.text("tool")
                                + "' executed without an authorizing scope");
                    }
                }
                case "handoff" -> {
                    if (e.flag("forwarded") && e.number("confidence", 0) < CONFIDENCE_THRESHOLD) {
                        found.add("CONFIDENCE_GATE_BYPASSED: handoff forwarded at confidence "
                                + e.number("confidence", 0) + " below threshold " + CONFIDENCE_THRESHOLD);
                    }
                    if (!e.flag("forwarded")) {
                        abstained = true;
                    }
                }
                case "memory_write" -> {
                    if (abstained) {
                        found.add("ABSTENTION_NOT_RESPECTED: memory written after the pipeline abstained");
                    }
                }
                default -> {
                    // step/plan events carry no invariant of their own
                }
            }
        }

        if (events.isEmpty() || !events.get(events.size() - 1).kind().equals("run_summary")) {
            found.add("NO_TERMINAL_SUMMARY: trace does not end with a run_summary event");
        }
        return found;
    }

    /**
     * Deterministic composite, 0-100:
     * <ul>
     *   <li>50 pts - the run reached a COMPLETED outcome</li>
     *   <li>20 pts x schema-valid handoff ratio</li>
     *   <li>20 pts x the final handoff's confidence</li>
     *   <li>10 pts x memory grounding (share of recalled facts this run itself produced)</li>
     *   <li>-15 pts per structural violation</li>
     * </ul>
     *
     * <p>Note what this deliberately does NOT reward: a correctly abstained run
     * scores low, because the score answers "did this run produce a shippable
     * result?", not "did the agent behave well?". Those are different
     * questions and conflating them is how a gate ends up green on a run that
     * delivered nothing. Read the violation list for the behaviour question -
     * a good abstention has a low score and zero violations.
     */
    public static double score(RunTrace trace) {
        double points = 0;

        boolean completed = trace.events("run_summary").stream()
                .anyMatch(e -> "COMPLETED".equals(e.text("outcome")));
        if (completed) {
            points += 50;
        }

        List<RunTrace.Event> handoffs = trace.events("handoff");
        if (!handoffs.isEmpty()) {
            long valid = handoffs.stream().filter(e -> e.flag("schemaValid")).count();
            points += 20.0 * valid / handoffs.size();
            points += 20.0 * handoffs.get(handoffs.size() - 1).number("confidence", 0);
        }

        List<RunTrace.Event> recalls = trace.events("memory_recall");
        if (!recalls.isEmpty()) {
            RunTrace.Event last = recalls.get(recalls.size() - 1);
            double returned = last.number("returned", 0);
            if (returned > 0) {
                points += 10.0 * Math.min(1.0, last.number("fromThisRun", 0) / returned);
            }
        }

        points -= violations(trace).size() * VIOLATION_PENALTY;

        return Math.max(0, Math.min(100, points));
    }
}

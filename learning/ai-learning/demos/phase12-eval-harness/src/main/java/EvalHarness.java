import java.util.List;

/**
 * Top-level orchestrator tying the three evaluation layers together into
 * two operations a CI pipeline would actually call:
 *
 *  - {@link #classify(RunTrace)}: SUCCESS/ESCALATE, for the golden-set
 *    regression test (see {@code GoldenSetRegressionTest}).
 *  - {@link #score(RunTrace)}: a single deterministic 0-100 number a CI
 *    gate could threshold on, for the regression-detection ranking demo
 *    (see {@code RegressionDetectionTest}). Deliberately NOT LLM-based --
 *    a CI gate needs a number that doesn't change between two runs over
 *    the exact same trace file.
 */
public final class EvalHarness {

    private EvalHarness() {
    }

    /**
     * Classifies a run as SUCCESS or ESCALATE purely from its own recorded
     * run_summary.outcome (the same field a human reads off the terminal).
     * This is intentionally the simplest possible classifier -- the golden
     * set's job is to prove that reading this one field correctly reproduces
     * all 6 known-correct labels; {@link TaskLevelEvaluator} separately
     * checks whether that field can be TRUSTED (i.e. it agrees with the
     * captured test outcome).
     */
    public static GoldenDataset.Outcome classify(RunTrace trace) {
        if (trace.recordedOutcomeIsSuccess()) {
            return GoldenDataset.Outcome.SUCCESS;
        }
        return GoldenDataset.Outcome.ESCALATE;
    }

    /**
     * Deterministic composite quality score, 0-100, no LLM. Weights:
     *  - 50 pts: final outcome is SUCCESS (pr-simulated)
     *  - 20 pts: test pass ratio (passed test_runs / total test_runs)
     *  - 20 pts: retry efficiency (fewer CODING attempts is better; first-try = full marks)
     *  - 10 pts: token efficiency (fewer total tokens is better, saturating past 12000)
     *  - -15 pts per structural conformance violation
     *  - scaled by average per-step "confidence" marker when the trace carries one (synthetic
     *    fixtures only -- real phase8 traces don't emit this field, so it's a neutral 1.0x there)
     */
    public static double score(RunTrace trace) {
        double points = 0;

        if (trace.recordedOutcomeIsSuccess()) {
            points += 50;
        }

        List<com.fasterxml.jackson.databind.JsonNode> testRuns = trace.testRuns();
        if (!testRuns.isEmpty()) {
            long passed = testRuns.stream().filter(t -> t.path("passed").asBoolean(false)).count();
            points += 20.0 * passed / testRuns.size();
        }

        int codingAttempts = trace.codingSteps().size();
        int retries = Math.max(0, codingAttempts - 1);
        points += Math.max(0, 20 - retries * 7.0);

        long totalTokens = trace.totalTokensIn() + trace.totalTokensOut();
        points += Math.max(0, 10.0 * (1.0 - totalTokens / 12000.0));

        int violations = StructuralConformanceValidator.validate(trace).size();
        points -= violations * 15.0;

        points *= trace.averageConfidenceOrNeutral();

        return Math.max(0, Math.min(100, points));
    }
}

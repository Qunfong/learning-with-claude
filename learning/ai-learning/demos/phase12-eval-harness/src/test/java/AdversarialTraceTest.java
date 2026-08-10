import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The core "verify against the trace, don't trust the summary" test --
 * literally the lesson phase4-agents/README draws from CodingAgentDemo's
 * runs 2/3 (a model's natural-language claim of success contradicted by
 * trace.jsonl and the file on disk), now applied to a hand-crafted
 * adversarial phase8-style trace: {@code run_summary.outcome} claims
 * "pr-simulated" (success) while the last real {@code test_run} recorded
 * {@code passed:false} (and {@code testPasses:0}).
 *
 * A harness that only reads run_summary.outcome would report this as a
 * clean SUCCESS. This test proves the harness does NOT do that.
 */
class AdversarialTraceTest {

    private static final String RESOURCE = "adversarial/claims-success-but-tests-failed.jsonl";

    @Test
    void taskLevelEvaluatorFlagsTheMismatch() throws IOException {
        RunTrace trace = TraceLoader.loadFromClasspath(RESOURCE);

        // The naive read of run_summary.outcome alone WOULD say success -- confirm that's really what it claims.
        assertTrue(trace.recordedOutcomeIsSuccess(), "fixture must actually claim success in run_summary.outcome");
        assertEquals(Boolean.FALSE, trace.lastTestPassed(), "fixture must actually have a failing last test_run");

        TaskLevelEvaluator.Result result = TaskLevelEvaluator.evaluate(trace);

        assertFalse(result.consistent(), "harness must flag outcome='pr-simulated' + last test_run.passed=false as INCONSISTENT");
        assertTrue(result.detail().contains("MISMATCH"), "detail message should clearly say MISMATCH, not bury it");
    }

    @Test
    void structuralValidatorIndependentlyCatchesIt() throws IOException {
        // Second, independent layer: GATE2 was reached without ever passing a test -- a structural
        // violation regardless of what run_summary claims. Two layers agreeing is stronger evidence
        // than either alone.
        RunTrace trace = TraceLoader.loadFromClasspath(RESOURCE);
        List<StructuralConformanceValidator.Violation> violations = StructuralConformanceValidator.validate(trace);

        boolean foundTestingBeforeGate2Violation = violations.stream().anyMatch(v -> v.rule().equals("TESTING_BEFORE_GATE2"));
        assertTrue(foundTestingBeforeGate2Violation, "expected a TESTING_BEFORE_GATE2 violation, got: " + violations);
    }

    @Test
    void aggregateScorePenalizesTheAdversarialTraceHeavily() throws IOException {
        // A naive scorer keying only off run_summary.outcome would give this ~70/100 (success + 1 test
        // run + first-try coding + low tokens). The structural-violation penalty in EvalHarness.score
        // must pull it down well below what a genuinely clean success run scores.
        RunTrace adversarial = TraceLoader.loadFromClasspath(RESOURCE);
        RunTrace genuineSuccess = TraceLoader.loadFromClasspath("phase8-traces/run-DEMO-001-1785276196928.jsonl");

        double adversarialScore = EvalHarness.score(adversarial);
        double genuineScore = EvalHarness.score(genuineSuccess);

        assertTrue(adversarialScore < genuineScore,
                "adversarial trace (score=" + adversarialScore + ") should score lower than a genuine clean success (score=" + genuineScore + ")");
    }
}

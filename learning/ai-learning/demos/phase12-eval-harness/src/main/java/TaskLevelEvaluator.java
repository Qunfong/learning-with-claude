/**
 * AWS three-layer framework, layer 1: task-level correctness.
 *
 * Exact-match logic, NO LLM involved: replay whether the trace's own
 * captured test outcome (the last {@code test_run.passed} -- the closest
 * thing an observability trace has to a recorded {@code mvn test} exit
 * code) matches the run's own {@code run_summary.outcome} claim
 * ("pr-simulated" == SUCCESS, "escalated (...)" == ESCALATE).
 *
 * This is deliberately dumb and deliberately distrustful -- it is the
 * literal implementation of phase4-agents/README's lesson ("never trust the
 * model's own summary, verify against the trace"), just aimed at phase8's
 * run_summary line instead of a model's natural-language claim. See
 * AdversarialTraceTest for the case this is built to catch: a run_summary
 * that claims "pr-simulated" while the last real test_run recorded
 * passed=false.
 */
public final class TaskLevelEvaluator {

    public record Result(String runId, String recordedOutcome, Boolean lastTestPassed, boolean consistent, String detail) {
    }

    private TaskLevelEvaluator() {
    }

    public static Result evaluate(RunTrace trace) {
        String recordedOutcome = trace.recordedOutcome();
        Boolean lastTestPassed = trace.lastTestPassed();

        if (recordedOutcome == null) {
            return new Result(trace.runId(), null, lastTestPassed, false, "no run_summary event -- cannot determine recorded outcome");
        }

        boolean claimsSuccess = trace.recordedOutcomeIsSuccess();
        boolean claimsEscalate = trace.recordedOutcomeIsEscalate();
        if (!claimsSuccess && !claimsEscalate) {
            return new Result(trace.runId(), recordedOutcome, lastTestPassed, false,
                    "run_summary.outcome '" + recordedOutcome + "' is neither a recognized success nor escalate vocabulary");
        }

        if (lastTestPassed == null) {
            // A claimed SUCCESS with zero recorded test runs is itself an inconsistency worth flagging;
            // an ESCALATE with zero test runs (e.g. budget-exceeded escalation) is legitimate.
            boolean ok = claimsEscalate;
            return new Result(trace.runId(), recordedOutcome, null, ok,
                    ok ? "no test_run recorded; consistent with a non-test escalation (e.g. token-budget rail)"
                       : "run_summary claims success but no test_run was ever recorded");
        }

        boolean consistent = (claimsSuccess && lastTestPassed) || (claimsEscalate && !lastTestPassed);
        String detail = consistent
                ? "last test_run.passed=" + lastTestPassed + " agrees with recorded outcome '" + recordedOutcome + "'"
                : "MISMATCH: last test_run.passed=" + lastTestPassed + " but run_summary.outcome='" + recordedOutcome + "'";
        return new Result(trace.runId(), recordedOutcome, lastTestPassed, consistent, detail);
    }
}

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the composite-score formula and the structural invariants, the two
 * things a CI gate would actually depend on. A scorer nobody has pinned is a
 * number nobody should threshold on.
 */
class CapstoneEvalTest {

    private static RunTrace completedRun() {
        RunTrace trace = new RunTrace("test-completed");
        trace.record("credential_minted", "credentialId", "cred-3", "principal", "SummarizerAgent",
                "scopes", "read:incident");
        trace.record("tool_call", "tool", "read_incident", "requiredScope", "read:incident",
                "authorized", true, "executed", true);
        trace.record("tool_call", "tool", "send_email", "requiredScope", "send:email",
                "authorized", false, "executed", false);
        trace.record("handoff", "schemaValid", true, "confidence", 0.86, "forwarded", true);
        trace.record("memory_write", "facts", 2);
        trace.record("memory_recall", "requested", 5, "returned", 3, "fromThisRun", 2);
        trace.record("run_summary", "outcome", "COMPLETED");
        return trace;
    }

    @Test
    void completedRunScoresExactlyTheDocumentedFormula() {
        // 50 (completed) + 20*1.0 (schema-valid) + 20*0.86 (confidence) + 10*(2/3) (grounding)
        double expected = 50 + 20 + 20 * 0.86 + 10 * (2.0 / 3.0);

        assertEquals(expected, CapstoneEval.score(completedRun()), 1e-9);
        assertTrue(CapstoneEval.score(completedRun()) >= CapstoneEval.CI_GATE,
                "a clean completed run must clear the CI gate");
    }

    @Test
    void abstainedRunScoresBelowTheGateButHasNoViolations() {
        RunTrace trace = new RunTrace("test-abstained");
        trace.record("credential_minted", "credentialId", "cred-3", "principal", "SummarizerAgent",
                "scopes", "read:incident");
        trace.record("tool_call", "tool", "read_incident", "requiredScope", "read:incident",
                "authorized", true, "executed", true);
        trace.record("handoff", "schemaValid", true, "confidence", 0.35, "forwarded", false);
        trace.record("run_summary", "outcome", "ABSTAINED");

        double score = CapstoneEval.score(trace);

        // 0 (not completed) + 20*1.0 (schema-valid) + 20*0.35 (confidence) + 0 (nothing recalled)
        assertEquals(20 + 20 * 0.35, score, 1e-9);
        assertTrue(score < CapstoneEval.CI_GATE, "an abstained run delivered nothing shippable");
        assertEquals(List.of(), CapstoneEval.violations(trace),
                "abstaining is CORRECT behaviour -- a low score must not imply a violation");
    }

    @Test
    void unauthorizedExecutionIsAStructuralViolation() {
        RunTrace trace = new RunTrace("test-unauthorized");
        trace.record("credential_minted", "credentialId", "cred-3", "principal", "SummarizerAgent",
                "scopes", "read:incident");
        trace.record("tool_call", "tool", "send_email", "requiredScope", "send:email",
                "authorized", false, "executed", true);
        trace.record("run_summary", "outcome", "COMPLETED");

        assertTrue(CapstoneEval.violations(trace).stream()
                        .anyMatch(v -> v.startsWith("UNAUTHORIZED_EXECUTION")),
                "an executor that ran without an authorizing scope must be caught off the trace, "
                        + "not off the agent's own account of the run");
    }

    @Test
    void toolCallBeforeAnyCredentialIsAStructuralViolation() {
        RunTrace trace = new RunTrace("test-no-credential");
        trace.record("tool_call", "tool", "read_incident", "requiredScope", "read:incident",
                "authorized", true, "executed", true);
        trace.record("run_summary", "outcome", "COMPLETED");

        assertTrue(CapstoneEval.violations(trace).stream()
                .anyMatch(v -> v.startsWith("TOOL_CALL_BEFORE_CREDENTIAL")));
    }

    @Test
    void writingMemoryAfterAbstainingIsAStructuralViolation() {
        RunTrace trace = new RunTrace("test-ignored-abstention");
        trace.record("credential_minted", "credentialId", "cred-3", "principal", "SummarizerAgent",
                "scopes", "read:incident");
        trace.record("handoff", "schemaValid", true, "confidence", 0.2, "forwarded", false);
        trace.record("memory_write", "facts", 2);
        trace.record("run_summary", "outcome", "COMPLETED");

        assertTrue(CapstoneEval.violations(trace).stream()
                        .anyMatch(v -> v.startsWith("ABSTENTION_NOT_RESPECTED")),
                "abstaining then storing the result anyway defeats the whole gate");
    }

    @Test
    void forwardingBelowThresholdIsAStructuralViolation() {
        RunTrace trace = new RunTrace("test-bypassed-gate");
        trace.record("credential_minted", "credentialId", "cred-3", "principal", "SummarizerAgent",
                "scopes", "read:incident");
        trace.record("handoff", "schemaValid", true, "confidence", 0.2, "forwarded", true);
        trace.record("run_summary", "outcome", "COMPLETED");

        assertTrue(CapstoneEval.violations(trace).stream()
                .anyMatch(v -> v.startsWith("CONFIDENCE_GATE_BYPASSED")));
    }

    @Test
    void aTraceThatDoesNotEndInASummaryIsIncomplete() {
        RunTrace trace = new RunTrace("test-truncated");
        trace.record("credential_minted", "credentialId", "cred-3", "principal", "SummarizerAgent",
                "scopes", "read:incident");
        trace.record("handoff", "schemaValid", true, "confidence", 0.9, "forwarded", true);

        assertTrue(CapstoneEval.violations(trace).stream()
                        .anyMatch(v -> v.startsWith("NO_TERMINAL_SUMMARY")),
                "a truncated trace must not be scored as if the run finished");
    }

    @Test
    void theThreeDemoRunsRankInTheDocumentedOrder() {
        RunTrace confident = CapstoneDemoTestSupport.silently(
                () -> CapstoneDemo.runChain("test A", SummarizerAgent.Mode.NORMAL));
        RunTrace lowConfidence = CapstoneDemoTestSupport.silently(
                () -> CapstoneDemo.runChain("test B", SummarizerAgent.Mode.LOW_CONFIDENCE));
        RunTrace malformed = CapstoneDemoTestSupport.silently(
                () -> CapstoneDemo.runChain("test C", SummarizerAgent.Mode.MALFORMED_HANDOFF));

        assertTrue(CapstoneEval.score(confident) > CapstoneEval.score(lowConfidence));
        assertTrue(CapstoneEval.score(lowConfidence) > CapstoneEval.score(malformed));
        assertTrue(CapstoneEval.score(confident) >= CapstoneEval.CI_GATE);
        assertTrue(CapstoneEval.score(lowConfidence) < CapstoneEval.CI_GATE);

        // The load-bearing assertion of the whole module: none of the three runs
        // violates a structural invariant. Two of them score badly, and both do
        // so by refusing to proceed -- which is the behaviour, not a bug in it.
        assertEquals(List.of(), CapstoneEval.violations(confident));
        assertEquals(List.of(), CapstoneEval.violations(lowConfidence));
        assertEquals(List.of(), CapstoneEval.violations(malformed));
    }
}

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit-level tests for the structural invariants themselves (as opposed to
 * GoldenSetRegressionTest, which checks that real traces satisfy them).
 * Builds tiny in-memory traces that deliberately violate each rule, so each
 * rule's detection logic is pinned down independently of any fixture file.
 */
class StructuralConformanceValidatorTest {

    @Test
    void catchesCodingBeforeGate1() throws IOException {
        RunTrace trace = TraceLoader.loadFromClasspath("structural/coding-before-gate1.jsonl");
        List<StructuralConformanceValidator.Violation> violations = StructuralConformanceValidator.validate(trace);
        assertTrue(violations.stream().anyMatch(v -> v.rule().equals("GATE1_BEFORE_CODING")), "violations: " + violations);
    }

    @Test
    void catchesGate2BeforeAnyPassingTest() throws IOException {
        RunTrace trace = TraceLoader.loadFromClasspath("adversarial/claims-success-but-tests-failed.jsonl");
        List<StructuralConformanceValidator.Violation> violations = StructuralConformanceValidator.validate(trace);
        assertTrue(violations.stream().anyMatch(v -> v.rule().equals("TESTING_BEFORE_GATE2")), "violations: " + violations);
    }

    @Test
    void cleanRealRunHasZeroViolations() throws IOException {
        RunTrace trace = TraceLoader.loadFromClasspath("phase8-traces/run-DEMO-001-1785276196928.jsonl");
        assertEquals(List.of(), StructuralConformanceValidator.validate(trace));
    }
}

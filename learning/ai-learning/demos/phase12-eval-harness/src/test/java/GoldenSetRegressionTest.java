import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PRIMARY test of this module: the harness must correctly classify all 6
 * real, historical phase8-autonomous runs documented in that phase's
 * README ("What actually happened"). This is the golden/regression dataset
 * per AWS's evaluation-dataset taxonomy -- hand-labeled, known-correct,
 * repeatable.
 *
 * If this test ever fails after a change to EvalHarness.classify(...), that
 * change made the harness WORSE at a task it used to get right -- treat it
 * as a regression, not as "the golden labels must be wrong" (they're
 * transcribed straight from a README documenting real, live runs).
 */
class GoldenSetRegressionTest {

    @Test
    void classifiesAllSixHistoricalRunsCorrectly() throws IOException {
        List<String> misclassified = new ArrayList<>();

        for (GoldenDataset.GoldenRun golden : GoldenDataset.RUNS) {
            RunTrace trace = TraceLoader.loadFromClasspath(golden.resourceName());
            GoldenDataset.Outcome actual = EvalHarness.classify(trace);

            if (actual != golden.expected()) {
                misclassified.add(golden.runIdSuffix() + ": expected " + golden.expected()
                        + " but harness classified " + actual + " (" + golden.reason() + ")");
            }
        }

        assertTrue(misclassified.isEmpty(),
                "golden-set regression FAILED for " + misclassified.size() + "/6 runs:\n"
                        + String.join("\n", misclassified));
    }

    @Test
    void everyGoldenRunIsAlsoTaskLevelConsistent() throws IOException {
        // A second, independent cross-check: not just "does classify() match the label" but
        // "does the recorded outcome actually agree with the recorded test result" -- i.e. every
        // real historical run should pass TaskLevelEvaluator too (they're real, not adversarial).
        List<String> inconsistent = new ArrayList<>();
        for (GoldenDataset.GoldenRun golden : GoldenDataset.RUNS) {
            RunTrace trace = TraceLoader.loadFromClasspath(golden.resourceName());
            TaskLevelEvaluator.Result result = TaskLevelEvaluator.evaluate(trace);
            if (!result.consistent()) {
                inconsistent.add(golden.runIdSuffix() + ": " + result.detail());
            }
        }
        assertTrue(inconsistent.isEmpty(), "unexpected task-level inconsistency in real golden runs:\n" + String.join("\n", inconsistent));
    }

    @Test
    void everyGoldenRunPassesStructuralConformance() throws IOException {
        List<String> violations = new ArrayList<>();
        for (GoldenDataset.GoldenRun golden : GoldenDataset.RUNS) {
            RunTrace trace = TraceLoader.loadFromClasspath(golden.resourceName());
            for (StructuralConformanceValidator.Violation v : StructuralConformanceValidator.validate(trace)) {
                violations.add(golden.runIdSuffix() + ": [" + v.rule() + "] " + v.detail());
            }
        }
        assertTrue(violations.isEmpty(), "unexpected structural violation(s) in real golden runs:\n" + String.join("\n", violations));
    }
}

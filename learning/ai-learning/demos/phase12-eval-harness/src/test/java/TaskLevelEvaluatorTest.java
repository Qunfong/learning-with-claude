import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit-level pin for TaskLevelEvaluator's exact-match rule, across the two
 * legitimate real-world shapes (SUCCESS with a passing last test, ESCALATE
 * with a failing last test) plus the adversarial mismatch case (covered in
 * depth by AdversarialTraceTest -- this class just adds a same-layer sanity
 * check on the two golden-run shapes).
 */
class TaskLevelEvaluatorTest {

    @Test
    void successRunWithPassingTestIsConsistent() throws IOException {
        RunTrace trace = TraceLoader.loadFromClasspath("phase8-traces/run-DEMO-001-1785276196928.jsonl");
        TaskLevelEvaluator.Result result = TaskLevelEvaluator.evaluate(trace);
        assertTrue(result.consistent(), result.detail());
    }

    @Test
    void escalatedRunWithFailingTestIsConsistent() throws IOException {
        RunTrace trace = TraceLoader.loadFromClasspath("phase8-traces/run-DEMO-001-1785275876344.jsonl");
        TaskLevelEvaluator.Result result = TaskLevelEvaluator.evaluate(trace);
        assertTrue(result.consistent(), result.detail());
    }
}
